package encoder;


import aws.CredentialsFetch;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.rabbitmq.client.*;
import infrastructure.instances.encoder.EncoderInstance;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class EncoderCore {

    private AmazonS3 amazonS3Client;

    private Channel channel;
    public static String ENCODING_REQUEST_QUEUE = "encoding-request-queue";
    private String bucket_name = "nico-encoder-bucket-frankfurt"; //TODO: FIX SO BUCKETS ARE CREATED DYNAMICALLY?

    public EncoderCore() {
        amazonS3Client = AmazonS3ClientBuilder.standard().withCredentials(CredentialsFetch.getCredentialsProvider()).withRegion(Regions.EU_CENTRAL_1).build();
    }

    public List<S3ObjectSummary> listFilesS3() {

        ListObjectsV2Result result = amazonS3Client.listObjectsV2(bucket_name);
        return result.getObjectSummaries();
    }


    public void convertAndUpload(String fileKeyName) throws IOException {

        File localFile = new File(fileKeyName);
        amazonS3Client.getObject(new GetObjectRequest(bucket_name, fileKeyName), localFile); //download unconverted file

        if (localFile.exists() && localFile.canRead()) {

            System.out.println("File successfully downloaded: " + localFile.getAbsolutePath());
            File convertedFile = changeExtension(localFile, ".avi"); // all files are converted to .avi atm

            ProcessBuilder pb = new ProcessBuilder("sudo",
                    "mencoder",
                    localFile.getAbsolutePath(),
                    "-o",
                    convertedFile.getName(),
                    "-oac",
                    "mp3lame",
                    "-ovc",
                    "lavc",
                    "-lavcopts",
                    "vcodec=mpeg1video",
                    "-of",
                    "avi");

            System.out.println("Running command: \n\t" + pb.command().toString());
            Process p = pb.start();
            try {
                System.out.println("Converting file...");
                p.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (p.exitValue() == 0) { // if thread if successful/file is converted
                uploadFileS3(convertedFile);
                localFile.delete();
                convertedFile.delete();
            } else {
                System.out.println("Something went wrong in thread: " + p.toString() + " when converting.");
            }
        }
    }

    private void uploadFileS3(File file) {

        TransferManager tm = TransferManagerBuilder.standard()
                .withS3Client(amazonS3Client)
                .build();

        if (!amazonS3Client.doesObjectExist(bucket_name, file.getName())) {

            PutObjectRequest request = new PutObjectRequest(bucket_name, file.getName(), new File(file.getPath()));

            request.setGeneralProgressListener(progressEvent -> System.out.println("Transferred bytes: " + progressEvent.getBytesTransferred()));
            Upload upload = tm.upload(request);

            try {
                upload.waitForCompletion();
                System.out.println("Upload " + file.getName() + " successful.");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

//        if (!amazonS3Client.doesObjectExist(bucket_name, file.getName())) {
//            try {
//                amazonS3Client.putObject(bucket_name, file.getName(), new File(file.getPath()));
//
//            } catch (AmazonServiceException e) {
//                System.err.println(e.getErrorMessage());
//            }
//            System.out.println("Upload " + file.getName() + " successful.");
//        }
    }

    private static File changeExtension(File f, String newExtension) {
        int i = f.getName().lastIndexOf('.');
        String name = f.getName().substring(0, i);
        Path currentRelativePath = Paths.get("");
        return new File(currentRelativePath.toAbsolutePath().toString() + "/" + name + newExtension);
    }

    private void initRabbitMQConnection() {

        try {

            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setHost("rabbitmq-cluster-loadbalancer-449114661.eu-central-1.elb.amazonaws.com");
            connectionFactory.setUsername("admin");
            connectionFactory.setPassword("kebabpizza");
            Connection connection = connectionFactory.newConnection();
            channel = connection.createChannel();
            AMQP.Queue.DeclareOk ok = channel.queueDeclare(ENCODING_REQUEST_QUEUE, true, false, false, null);

        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startEncoderInstance() {
        try {

            AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();
            EncoderInstance.start(cp);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }


    public static void main(String[] args) throws IOException, TimeoutException {

        EncoderCore core = new EncoderCore();
//        core.startEncoderInstance();
        core.initRabbitMQConnection();

        for (S3ObjectSummary list : core.listFilesS3()) {
            System.out.println(list);
        }

        core.channel.basicQos(1); // accept only one unack-ed message at a time

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                String message = new String(delivery.getBody(), "UTF-8");
                System.out.println(" [x] Received '" + message + "'");
                core.convertAndUpload(message);
            } finally {

                core.channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };
        /*auto ack is false, will ack when work is done i.e. file is converted and uploaded*/
        /*TODO: Send message to client with link to converted/uploaded file*/
        core.channel.basicConsume(ENCODING_REQUEST_QUEUE, false, deliverCallback, consumerTag -> {
        });
    }
}

