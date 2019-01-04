package encoder;

import aws.CredentialsFetch;
import client.Message;
import client.prototypes.QueueChannelWrapper;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysRequest;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysResult;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferProgress;
import com.google.gson.Gson;
import com.rabbitmq.client.*;
import infrastructure.instances.manager.ManagerInstance;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

public class EncoderCore {

    private Channel channel;
    private Connection connection;

    private AmazonS3Client amazonS3Client;
    private AmazonEC2Client amazonEC2Client;

    public static String ENCODING_REQUEST_QUEUE = "encoding-request-queue";
    private String bucket_name = "kgencoderbucket";

    public EncoderCore() {

        amazonS3Client = new AmazonS3Client(getAwsCredentials());
        amazonEC2Client = new AmazonEC2Client(getAwsCredentials());


    }


    public BasicAWSCredentials getAwsCredentials() {
        return new BasicAWSCredentials(CredentialsFetch.getCredentialsProvider().getCredentials().getAWSAccessKeyId(),
                CredentialsFetch.getCredentialsProvider().getCredentials().getAWSSecretKey());
    }

    public List<S3ObjectSummary> listFilesS3() {

        ListObjectsV2Result result = amazonS3Client.listObjectsV2(bucket_name);
        return result.getObjectSummaries();
    }

    private void uploadConvertedFileToS3(String fileName, String path){


        if(!amazonS3Client.doesObjectExist(bucket_name, fileName)) {
            System.out.println("Uploading...");
            try {
                amazonS3Client.putObject(bucket_name, fileName, new File(path));
            } catch (AmazonServiceException e) {
                System.err.println(e.getErrorMessage());
            }
            System.out.println("Done!");
        }
    }


    public void getFileFromS3(String keyName) throws IOException {

        System.out.println("Saving file to: " + "movies/unconverted");

        //This is where the downloaded file will be saved
        File localFile = new File("movies/unconverted/".concat(keyName));
        amazonS3Client.getObject(new GetObjectRequest(bucket_name, keyName), localFile);

        if(localFile.exists() && localFile.canRead()) {

            System.out.println("File successfully downloaded: " + localFile.getAbsolutePath());
            System.out.println("Converting file...");


            ProcessBuilder pb = new ProcessBuilder("mencoder", localFile.getAbsolutePath(), "mp3lame", "-ovc", "lavc", "-o", localFile.getName().concat("converted").concat(".avi"));
            Process p = pb.start();
            try {
                p.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(p.exitValue() == 0){
                uploadConvertedFileToS3(keyName.concat(".mp4"), "movies/converted/");
            }
        }
    }


    private void displayTextInputStream(InputStream input) throws IOException {
        // Read the text input stream one line at a time and display each line.
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String line = null;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
        System.out.println();
    }



    public void initRabbitMQConnection() {

        try {

            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setHost("queue.ndersson.io");
            connectionFactory.setUsername("admin");
            connectionFactory.setPassword("kebabpizza");
            connection = connectionFactory.newConnection();
            channel = connection.createChannel();
            AMQP.Queue.DeclareOk ok = channel.queueDeclare(ENCODING_REQUEST_QUEUE, true, false, false, null);

        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createEncoderInstance() {


        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

        runInstancesRequest.withImageId("ami-0ac019f4fcb7cb7e6") // subject to change
                .withInstanceType(InstanceType.T2Micro) // subject to change
                .withMinCount(1)
                .withMaxCount(1)
                .withUserData(Base64.getEncoder().encodeToString(new Scanner(ManagerInstance.class.getResourceAsStream("/encoder-instance.yml"), "UTF-8").useDelimiter("\\A").next().getBytes()))
                .withKeyName("my-key-pair");

        RunInstancesResult result = amazonEC2Client.runInstances(
                runInstancesRequest);

        System.out.println("Encoder instance launched with configuration: " + result);
    }


    public static void main(String[] args) {


        EncoderCore core = new EncoderCore();
        core.createEncoderInstance();

        //        core.initRabbitMQConnection();
//
//
//        for (S3ObjectSummary list : core.listFilesS3()) {
//
//            System.out.println(list);
//        }
//
//
//        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
//            String message = new String(delivery.getBody(), "UTF-8");
//            System.out.println(" [x] Received '" + message + "'");
//            core.getFileFromS3(message);
//        };
//        try {
//            core.channel.basicConsume(ENCODING_REQUEST_QUEUE, true, deliverCallback, consumerTag -> { });
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }
}
