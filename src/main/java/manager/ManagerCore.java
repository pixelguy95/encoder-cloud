package manager;

import aws.CredentialsFetch;
import client.prototypes.QueueChannelWrapper;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.util.EC2MetadataUtils;
import com.rabbitmq.client.AMQP;
import infrastructure.instances.encoder.EncoderInstance;
import infrastructure.instances.manager.ManagerInstance;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class ManagerCore implements Runnable {

    public static QueueChannelWrapper qcw;
    private boolean replica;
    private AmazonEC2 ec2Client;

    private String bucketName;
    private String queueURL;

    public ManagerCore(String bucketName, String queueURL) throws InterruptedException, IOException, TimeoutException {

        this.bucketName = bucketName;
        this.queueURL = queueURL;

        qcw = new QueueChannelWrapper(queueURL);
        log("MANAGER STARTING");

        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();

        ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        if (countManagerInstances() > 1) {
            replica = true;
        }

        while (replica) {
            Thread.sleep(5000);
            if (countManagerInstances() == 1) {
                log("manager must have died: " + countManagerInstances());
                replica = false;
            }
        }

        log("starting replica");
        startReplica();
        log("replica started");

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

    public int countManagerInstances() {
        AtomicInteger count = new AtomicInteger();
        try {
            ec2Client.describeInstances().getReservations()
                    .forEach(reservation -> reservation.getInstances().stream().filter(i -> i.getState().getCode() == 16)
                            .forEach(instance -> instance.getTags().forEach(tag -> {
                                if (tag.getValue().startsWith("manager")) {
                                    count.getAndIncrement();
                                }
                            })));
        } catch (Exception e) {
            log(e.getMessage());
        }

        return count.get();
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
                ok = qcw.channel.queueDeclare(QueueChannelWrapper.ENCODING_REQUEST_QUEUE, true, false, false, null);
                double queueSize = ok.getMessageCount();
                double nrOfEncoders = ok.getConsumerCount();

                log("Encoding queue size: " + queueSize + ", Nr of encoders: " + nrOfEncoders);

                if (queueSize > nrOfEncoders + 1) {
                    log("New encoder instance needed");
                    startNewEncoder(nrOfEncoders, (int) (queueSize - nrOfEncoders));

                    log("Sleeping for 30 seconds now");

                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if(queueSize == 0 && nrOfEncoders > 1) {
                    killEncoders(nrOfEncoders - 1);
                }

            } catch (IOException e) {
                log(e.getMessage());
            }

        }
    }

    private void killEncoders(double encodersToKill) {
        List<Reservation> reservations = ec2Client.describeInstances().getReservations();

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

                        if(killed >= encodersToKill)
                            break;

                        TerminateInstancesRequest tir = new TerminateInstancesRequest();
                        tir.setInstanceIds(Arrays.asList(instance.getInstanceId()));
                        ec2Client.terminateInstances(tir);
                        killed++;

                        break;
                    }
                }

                if(killed >= encodersToKill)
                    break;
            }

            if(killed >= encodersToKill)
                break;

        }

        log("Killed " + killed + " encoders");
    }

    private void startNewEncoder(double currentNrOfEncoders, int encodersToCreate) {

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

                    for (Image image : all) {
                        if (image.getName().equals("encoder-instance-image-v1") && image.getState().equals("available")) {
                            break;
                        }

                        log(image.getState() + " " + image.getState().equals("available"));
                    }
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

    private void startBrandNewEncoder(int encodersToCreate) {
        log("Starting new encoder(s) + " + encodersToCreate + " from scratch, will take time to boot properly");
        EncoderInstance.start(CredentialsFetch.getCredentialsProvider(), bucketName, queueURL, encodersToCreate);

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

    public static void main(String args[]) throws IOException, TimeoutException, InterruptedException {


        new ManagerCore(args[0], args[1]);
    }

    /**
     * Temporary way of publishing log messages
     *
     * @param message message will be written to the basic log queue
     */
    public static void log(String message) {
        try {

            String identity = "[unknown]";
            try {
                identity = "[" + EC2MetadataUtils.getInstanceId() + "]";
            } catch (Exception e) {

            }

            message = identity + " " + message;
            qcw.channel.basicPublish("", QueueChannelWrapper.BASIC_LOG_QUEUE, null, message.getBytes());
        } catch (IOException e) {
        }
    }
}
