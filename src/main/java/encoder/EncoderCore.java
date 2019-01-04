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
        ;;

    }


    public BasicAWSCredentials getAwsCredentials() {
        return new BasicAWSCredentials(CredentialsFetch.getCredentialsProvider().getCredentials().getAWSAccessKeyId(),
                CredentialsFetch.getCredentialsProvider().getCredentials().getAWSSecretKey());
    }

    public File getFile() throws URISyntaxException {

        return new File(EncoderCore.class.getResource("/sample_video.mp4").toURI());

    }


    public List<S3ObjectSummary> listFilesS3() {

        ListObjectsV2Result result = amazonS3Client.listObjectsV2(bucket_name);
        return result.getObjectSummaries();
    }


    public void getFileFromS3(String key) throws IOException {

        S3Object fullObject = null, objectPortion = null, headerOverrideObject = null;
        try {

            // Get an object and print its contents.
            System.out.println("Downloading an object");
            fullObject = amazonS3Client.getObject(new GetObjectRequest(bucket_name, key));
            System.out.println("Content-Type: " + fullObject.getObjectMetadata().getContentType());
            System.out.println("Content: ");
            displayTextInputStream(fullObject.getObjectContent());

            // Get a range of bytes from an object and print the bytes.
            GetObjectRequest rangeObjectRequest = new GetObjectRequest(bucket_name, key)
                    .withRange(0,9);
            objectPortion = amazonS3Client.getObject(rangeObjectRequest);
            System.out.println("Printing bytes retrieved.");
            displayTextInputStream(objectPortion.getObjectContent());

            // Get an entire object, overriding the specified response headers, and print the object's content.
            ResponseHeaderOverrides headerOverrides = new ResponseHeaderOverrides()
                    .withCacheControl("No-cache")
                    .withContentDisposition("attachment; filename=example.txt");
            GetObjectRequest getObjectRequestHeaderOverride = new GetObjectRequest(bucket_name, key)
                    .withResponseHeaders(headerOverrides);
            headerOverrideObject = amazonS3Client.getObject(getObjectRequestHeaderOverride);
            displayTextInputStream(headerOverrideObject.getObjectContent());
        }
        catch(AmazonServiceException e) {
            // The call was transmitted successfully, but Amazon S3 couldn't process
            // it, so it returned an error response.
            e.printStackTrace();
        }
        catch(SdkClientException e) {
            // Amazon S3 couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon S3.
            e.printStackTrace();
        }
        finally {
            // To ensure that the network connection doesn't remain open, close any open input streams.
            if(fullObject != null) {
                fullObject.close();
            }
            if(objectPortion != null) {
                objectPortion.close();
            }
            if(headerOverrideObject != null) {
                headerOverrideObject.close();
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
        core.initRabbitMQConnection();


        for (S3ObjectSummary list : core.listFilesS3()) {

            System.out.println(list);
        }

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [x] Received '" + message + "'");
            core.getFileFromS3(message); //TODO: Tror inte jag har permission från KG's bucket att hämta filer.
        };
        try {
            core.channel.basicConsume(ENCODING_REQUEST_QUEUE, true, deliverCallback, consumerTag -> { });
        } catch (IOException e) {
            e.printStackTrace();
        }


//
//        try {
//
//            PutObjectRequest uploadRequest = new PutObjectRequest("nico-encoder-bucket", "converted/" + core.getFile().getName(), core.getFile());
//            PutObjectResult uploadResponse = core.s3Client.putObject(uploadRequest);
//            System.out.println(uploadResponse);
//
//
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//        }

    }
}
