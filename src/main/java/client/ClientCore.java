package client;

import aws.CredentialsFetch;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.AmazonServiceException;
import com.google.gson.Gson;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeoutException;



public class ClientCore {

    private static final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withCredentials(CredentialsFetch.getCredentialsProvider()).withRegion(Regions.EU_CENTRAL_1).build();
    private static Channel channel;
    private static Connection connection;
    private static String ENCODING_REQUEST_QUEUE = "encoding-request-queue";
    private static String BASIC_LOG_QUEUE = "basic-log-queue";

    public static void main(String[] args) throws IOException, TimeoutException {
        //createBucket("kgencoderbucket");

        upload("kgencoderbucket","unconverted/polarBear", "movies/SampleVideo_1280x720_1mb.mp4");


        openConnectionToMQ();

        declareQueues(channel);
        sendMessage("unconverted/polarBear", channel);

        shutdown(channel, connection);
    }

    public static void createBucket(String bucketName) {
        System.out.println("Create bucket");
        s3.createBucket(bucketName);
    }


    public static void upload(String bucketName, String keyName, String path) {
        System.out.println("Uploading...");

        try {
            s3.putObject(bucketName, keyName, new File(path));
        } catch (AmazonServiceException e) {
            System.err.println(e.getErrorMessage());
            System.exit(1);
        }
        System.out.println("Done!");
    }

    public static void openConnectionToMQ() throws IOException, TimeoutException {

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("queue.ndersson.io");
        connectionFactory.setUsername("admin");
        connectionFactory.setPassword("kebabpizza");
        connection = connectionFactory.newConnection();
        channel = connection.createChannel();

    }

    private static void shutdown(Channel channel, Connection connection) throws IOException, TimeoutException {
        channel.close();
        connection.close();
    }

    private static void sendMessage(String key, Channel channel) throws IOException {

        channel.basicPublish("", ENCODING_REQUEST_QUEUE, null, buildMQMessage(key).getBytes());
    }

    public static String buildMQMessage(String key) {
        Message message = new Message();

        message.setKey(key);

        Gson gson = new Gson();

        return gson.toJson(message);
    }

    public static void declareQueues(Channel channel) throws IOException {
        AMQP.Queue.DeclareOk ok = channel.queueDeclare(ENCODING_REQUEST_QUEUE, true, false, false, null);
        ok = channel.queueDeclare(BASIC_LOG_QUEUE, true, false, false, null);
    }
}
