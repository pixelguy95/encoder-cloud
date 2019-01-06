package encoder;

import aws.CredentialsFetch;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
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

    public BasicAWSCredentials getAwsCredentials() {
        return new BasicAWSCredentials(CredentialsFetch.getCredentialsProvider().getCredentials().getAWSAccessKeyId(),
                CredentialsFetch.getCredentialsProvider().getCredentials().getAWSSecretKey());
    }

    public List<S3ObjectSummary> listFilesS3() {

        ListObjectsV2Result result = amazonS3Client.listObjectsV2(bucket_name);
        return result.getObjectSummaries();
    }


    public void convertAndUpload(String fileKeyName) throws IOException {

        //This is where the downloaded file will be saved
        File localFile = new File(fileKeyName);
        amazonS3Client.getObject(new GetObjectRequest(bucket_name, fileKeyName), localFile);

        if (localFile.exists() && localFile.canRead()) {

            System.out.println("File successfully downloaded: " + localFile.getAbsolutePath());

            File convertedFile = changeExtension(localFile, ".avi");

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
            if (p.exitValue() == 0) {

                if (!amazonS3Client.doesObjectExist(bucket_name, convertedFile.getName())) {
                    System.out.println("Uploading...");
                    try {
                        amazonS3Client.putObject(bucket_name, convertedFile.getName(), new File(convertedFile.getPath()));
                    } catch (AmazonServiceException e) {
                        System.err.println(e.getErrorMessage());
                    }
                    System.out.println("Upload " + convertedFile.getName() + "successful.");
                    localFile.delete();
                    convertedFile.delete();
                }
            }
        }
    }

    public static File changeExtension(File f, String newExtension) {
        int i = f.getName().lastIndexOf('.');
        String name = f.getName().substring(0,i);
        Path currentRelativePath = Paths.get("");
        return new File(currentRelativePath.toAbsolutePath().toString() + "/" + name + newExtension);
    }

    public void initRabbitMQConnection() {

        try {

            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setHost("queue.ndersson.io");
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

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [x] Received '" + message + "'");
            core.convertAndUpload(message);
        };
        try {
            core.channel.basicConsume(ENCODING_REQUEST_QUEUE, true, deliverCallback, consumerTag -> {
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}