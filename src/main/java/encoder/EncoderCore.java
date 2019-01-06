package encoder;


import client.prototypes.QueueChannelWrapper;
import java.io.*;
import java.util.concurrent.TimeoutException;

public class EncoderCore {

    private QueueChannelWrapper queueChannelWrapper;

    public EncoderCore(String bucketName, String url) throws IOException, TimeoutException {
        queueChannelWrapper = new QueueChannelWrapper(url);
        queueChannelWrapper.channel.basicQos(1); // accept only one unack-ed message at a time

        EncoderConsumer consumer = new EncoderConsumer(queueChannelWrapper, bucketName);

        queueChannelWrapper.channel.basicConsume(QueueChannelWrapper.ENCODING_REQUEST_QUEUE, false, consumer, consumerTag -> {});
    }

    public static void main(String[] args) throws IOException, TimeoutException {
        new EncoderCore(args[0], args[1]);
    }
}

