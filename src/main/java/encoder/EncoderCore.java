package encoder;

import aws.CredentialsFetch;
import client.prototypes.QueueChannelWrapper;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.rabbitmq.client.*;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

public class EncoderCore {

    private QueueChannelWrapper queueChannelWrapper;
    private String bucketName = "temporary-encoder-bucket-to-be-deleted";

    public EncoderCore(String bucketName, String url) throws IOException, TimeoutException {
        queueChannelWrapper = new QueueChannelWrapper("rabbitmq-cluster-loadbalancer-449114661.eu-central-1.elb.amazonaws.com");
        queueChannelWrapper.channel.basicQos(1); // accept only one unack-ed message at a time

        EncoderConsumer consumer = new EncoderConsumer(queueChannelWrapper, this.bucketName);

        queueChannelWrapper.channel.basicConsume(QueueChannelWrapper.ENCODING_REQUEST_QUEUE, false, consumer, consumerTag -> {
        });
    }

    public static void main(String[] args) throws IOException, TimeoutException {
        new EncoderCore("hej", "då"); //TODO: set arguments here
    }
}

