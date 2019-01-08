package manager;

import aws.CredentialsFetch;
import client.prototypes.QueueChannelWrapper;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.connect.model.GetMetricDataRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.util.EC2MetadataUtils;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.http.client.Client;
import com.rabbitmq.http.client.domain.QueueInfo;
import infrastructure.instances.encoder.EncoderInstance;
import infrastructure.instances.manager.ManagerInstance;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ManagerCore implements Runnable {

    public static QueueChannelWrapper qcw;
    public static Client rabbitMQClusterClient;
    private boolean replica;
    private AmazonEC2 ec2Client;

    private String bucketName;
    private String queueURL;

    public ManagerCore(String bucketName, String queueURL) throws InterruptedException, IOException, TimeoutException, URISyntaxException {


        this.bucketName = bucketName;
        this.queueURL = queueURL;

        rabbitMQClusterClient = new Client("http://" + queueURL + ":15672/api/", "admin", "kebabpizza");

        qcw = new QueueChannelWrapper(queueURL);
        log("MANAGER STARTING");

        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();

        ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        if (countRunningInstancesWithTag("manager") > 1) {
            replica = true;
        }

        while (replica) {
            Thread.sleep(5000);
            int managers = countRunningInstancesWithTag("manager");
            if (managers == 1) {
                log("manager must have died: " + managers);
                replica = false;
            }
        }

        log("starting replica");
        startReplica();
        log("replica started");

        log("Starting data reporting");
        new Thread(new Runnable() {
            @Override
            public void run() {

                AmazonCloudWatch cloudWatchCLient = AmazonCloudWatchClientBuilder
                        .standard()
                        .withCredentials(cp)
                        .withRegion(Regions.EU_CENTRAL_1)
                        .build();

                String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
                int encoders = countRunningInstancesWithTag("encoder");

                while(true) {

                    String dataLine = time + ",";
                    dataLine = dataLine + "," + encoders;

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    List<Instance> allEncoders = getInstancesWithTag("encoder");
                    double sum = 0.0;
                    for(Instance encoder : allEncoders) {
                        GetMetricStatisticsRequest gmdr = new GetMetricStatisticsRequest();
                        gmdr.setMetricName("CPUUtilization");
                        gmdr.setStatistics(Arrays.asList("Maximum"));
                        gmdr.setDimensions(Arrays.asList(new Dimension().withName("InstanceId").withValue(encoder.getInstanceId())));
                        Calendar c = Calendar.getInstance();
                        c.add(Calendar.SECOND, -4*60);
                        gmdr.setStartTime(c.getTime());
                        gmdr.setPeriod(60);
                        gmdr.setEndTime(Calendar.getInstance().getTime());
                        gmdr.setNamespace("AWS/EC2");
                        GetMetricStatisticsResult res = cloudWatchCLient.getMetricStatistics(gmdr);

                        Datapoint latest = res.getDatapoints().stream().sorted(Comparator.comparing(Datapoint::getTimestamp)).collect(Collectors.toList()).get(res.getDatapoints().size()-1);
                        sum += latest.getMaximum();
                    }

                    dataLine = dataLine + "," + sum;
                    data(dataLine);

                    time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
                    encoders = countRunningInstancesWithTag("encoder");
                }
            }
        });

        new Thread(this).start();
    }

    private void startReplica() {
        try {
            AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();
            ManagerInstance.start(cp, bucketName, queueURL);
        } catch (Exception e) {
            ManagerCore.log(e.getMessage());
        }

    }

    public int countRunningInstancesWithTag(String startsWith) {
        AtomicInteger count = new AtomicInteger();
        try {
            ec2Client.describeInstances().getReservations()
                    .forEach(reservation -> reservation.getInstances().stream().filter(i -> i.getState().getCode() == 16)
                            .forEach(instance -> instance.getTags().forEach(tag -> {
                                if (tag.getValue().startsWith(startsWith)) {
                                    count.getAndIncrement();
                                }
                            })));
        } catch (Exception e) {
            log(e.getMessage());
        }

        return count.get();
    }

    public List<Instance> getInstancesWithTag(String startsWith) {

        List<Instance> all = new ArrayList<>();
        try {
            ec2Client.describeInstances().getReservations()
                    .forEach(reservation -> reservation.getInstances().stream().filter(i -> i.getState().getCode() == 16)
                            .forEach(instance -> instance.getTags().forEach(tag -> {
                                if (tag.getValue().startsWith(startsWith)) {
                                    all.add(instance);
                                }
                            })));
        } catch (Exception e) {
            log(e.getMessage());
        }

        return all;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            AMQP.Queue.DeclareOk ok = null;
            try {
                ok = qcw.channel.queueDeclare(QueueChannelWrapper.ENCODING_REQUEST_QUEUE, true, false, false, null); //Just in case

                double queueSize = ok.getMessageCount();
                double nrOfEncoders = ok.getConsumerCount();

                log("Encoding queue size: " + queueSize + ", Nr of encoders: " + nrOfEncoders);

                if (queueSize > nrOfEncoders * 1.2) {
                    log("New encoder instance needed");
                    startNewEncoder(nrOfEncoders, (int) Math.floor(queueSize - (nrOfEncoders * 1.2)));

                    log("Sleeping for 30 seconds now");

                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (queueSize < nrOfEncoders && nrOfEncoders > 1) {

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    QueueInfo queueInfo = rabbitMQClusterClient.getQueue("/", QueueChannelWrapper.ENCODING_REQUEST_QUEUE);
                    double unAcked = queueInfo.getMessagesUnacknowledged();

                    log(queueSize + " " + unAcked + " " + nrOfEncoders);

                    if (queueSize + unAcked < nrOfEncoders) {
                        log("Killing " + Math.max(nrOfEncoders - queueSize - unAcked, 0) + " instances");
                        killEncoders(Math.max(nrOfEncoders - queueSize - unAcked, 0));
                    }
                }

            } catch (IOException | InterruptedException e) {
                log(e.getMessage());
            }

        }
    }

    private void killEncoders(double encodersToKill) {
        List<Reservation> reservations = ec2Client.describeInstances().getReservations();

        List<Instance> toKill = new ArrayList<>();
        int killed = 0;
        log("Going through, all instances, killing encoders until satisfied");
        for (Reservation r : reservations) {
            List<Instance> instances = r.getInstances();
            for (Instance instance : instances) {
                List<Tag> tags = instance.getTags();
                for (Tag tag : tags) {
                    if (tag.getKey().equals("infrastructure-type")
                            && tag.getValue().startsWith("encoder ")
                            && instance.getState().getCode() == 16) {

                        if (killed >= encodersToKill)
                            break;

                        TerminateInstancesRequest tir = new TerminateInstancesRequest();
                        tir.setInstanceIds(Arrays.asList(instance.getInstanceId()));
                        ec2Client.terminateInstances(tir);

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        killed++;

                        break;
                    }
                }

                if (killed >= encodersToKill)
                    break;
            }

            if (killed >= encodersToKill)
                break;

        }

        log("Killed " + killed + " encoders");
    }

    private void startNewEncoder(double currentNrOfEncoders, int encodersToCreate) throws IOException, InterruptedException {

        log("Going through all existing images");
        List<Image> images = null;
        try {
            DescribeImagesRequest dir = new DescribeImagesRequest();
            dir.setOwners(Arrays.asList("self"));
            images = ec2Client.describeImages(dir).getImages();
        } catch (Exception e) {
            log(e.getMessage());
        }

        boolean containsEncoderImage = false;
        Image encoderImage = null;
        log("Images found: " + images.size());
        for (Image i : images) {
            if (i.getName().equals("encoder-instance-image-v1")) {
                containsEncoderImage = true;
                encoderImage = i;
            }
        }
        log("Found existing encoder image? " + containsEncoderImage);

        if (!containsEncoderImage && currentNrOfEncoders > 0) {
            encoderImage = createEncoderImage();

            log("Waiting for image to become available");
            try {
                while (true) {
                    DescribeImagesRequest dir = new DescribeImagesRequest();
                    dir.setOwners(Arrays.asList("self"));
                    List<Image> all = ec2Client.describeImages(dir).getImages();
                    boolean done = false;

                    for (Image image : all) {
                        if (image.getName().equals("encoder-instance-image-v1") && image.getState().equals("available")) {
                            log("Available!");
                            done = true;
                            break;
                        }

                        log(image.getState() + " " + image.getState().equals("available"));
                    }

                    if (done)
                        break;

                    Thread.sleep(5000);
                }

            } catch (Exception e) {
                log(e.getMessage());
            }

            log("Image is available now");

            startEncoderFromImage(encoderImage, encodersToCreate);
        } else if (containsEncoderImage) {
            startEncoderFromImage(encoderImage, encodersToCreate);
        } else if (!containsEncoderImage && currentNrOfEncoders == 0) {
            startBrandNewEncoder(encodersToCreate);
        }
    }

    private void startBrandNewEncoder(int encodersToCreate) throws InterruptedException, IOException {
        log("Starting new encoder(s) + " + encodersToCreate + " from scratch, will take time to boot properly");
        EncoderInstance.start(CredentialsFetch.getCredentialsProvider(), bucketName, queueURL, encodersToCreate);

        while (true) {
            double nrOfEncoders = qcw.channel.queueDeclare(QueueChannelWrapper.ENCODING_REQUEST_QUEUE, true, false, false, null).getConsumerCount();

            if (nrOfEncoders > 0) {
                break;
            } else {
                log("Waiting for new instance to start...");
                Thread.sleep(6000);
            }
        }

        log("Done!, Sleeping extra 60 seconds");
        try {
            Thread.sleep(60000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startEncoderFromImage(Image encoderImage, int encodersToCreate) {
        log("Starting new encoder(s) + " + encodersToCreate + " from image");
        EncoderInstance.start(encoderImage.getImageId(), CredentialsFetch.getCredentialsProvider(), bucketName, queueURL, encodersToCreate);

        log("Done!");
    }

    private Image createEncoderImage() {
        log("Creating fresh encoder image");

        Instance createImageFrom = null;
        List<Reservation> reservations = ec2Client.describeInstances().getReservations();

        log("Going through, all instances");
        for (Reservation r : reservations) {
            List<Instance> instances = r.getInstances();
            for (Instance instance : instances) {
                List<Tag> tags = instance.getTags();
                for (Tag tag : tags) {
                    if (tag.getKey().equals("infrastructure-type")
                            && tag.getValue().startsWith("encoder ")
                            && instance.getState().getCode() == 16) {
                        createImageFrom = instance;
                        break;
                    }
                }

                if (createImageFrom != null)
                    break;
            }

            if (createImageFrom != null)
                break;
        }

        log("Found viable instance " + createImageFrom.getInstanceId());
        log("Sending image creation request");

        CreateImageRequest cir = new CreateImageRequest();
        cir.setInstanceId(createImageFrom.getInstanceId());
        cir.setName("encoder-instance-image-v1");

        String imageID = "(nothing yet)";
        try {
            imageID = ec2Client.createImage(cir).getImageId();
        } catch (Exception e) {
            log(e.getMessage());
        }

        log("Image created! " + imageID);

        return ec2Client.describeImages(new DescribeImagesRequest().withImageIds(imageID)).getImages().get(0);
    }

    public static void main(String args[]) throws IOException, TimeoutException, InterruptedException, URISyntaxException {


        new ManagerCore(args[0], args[1]);
    }

    /**
     * Temporary way of publishing log messages
     *
     * @param message message will be written to the basic log queue
     */
    public static void log(String message) {

        String identity = "[unknown]";
        try {
            identity = "[" + EC2MetadataUtils.getInstanceId() + "]";
        } catch (Exception e) {

        }

        message = identity + " " + message;
        publish(message);

    }

    public static void publish(String m) {
        try {
            qcw.channel.basicPublish("", QueueChannelWrapper.BASIC_LOG_QUEUE, null, m.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void data(String message) {
        String prefix = "[data] ";
        publish(prefix + message);
    }
}
