package client;

import aws.CredentialsFetch;
import client.prototypes.QueueChannelWrapper;
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

    public static void main(String[] args) throws IOException, TimeoutException {

        if(args.length < 3) {
            System.out.println("Not enough inputs");
            System.out.println("Arguments are: nameOfBucket, pathToFile, UniqueKey");
            System.exit(0);
        }

        QueueChannelWrapper channelWrapper = new QueueChannelWrapper();

        createBucket(args[0]);

        upload(args[0],args[2], args[1]);

        sendMessage(args[2], channelWrapper.channel);
    }

    private static void createBucket(String bucketName) {
        if(!s3.doesBucketExistV2(bucketName)) {
            System.out.println("Create bucket");
            s3.createBucket(bucketName);
        }
    }


    private static void upload(String bucketName, String keyName, String path) {

        if(!s3.doesObjectExist(bucketName,keyName)) {
            System.out.println("Uploading...");
            try {
                s3.putObject(bucketName, keyName, new File(path));
            } catch (AmazonServiceException e) {
                System.err.println(e.getErrorMessage());
                System.exit(1);
            }
            System.out.println("Done!");
        }
    }

    private static void sendMessage(String key, Channel channel) throws IOException {
        System.out.println("Sending to MQ");
        channel.basicPublish("", QueueChannelWrapper.ENCODING_REQUEST_QUEUE, null, buildMQMessage(key).getBytes());
    }

    private static String buildMQMessage(String key) {
        Message message = new Message();
        message.setKey(key);

        Gson gson = new Gson();
        return gson.toJson(message);
    }
}
