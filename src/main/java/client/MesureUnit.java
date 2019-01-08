package client;

import client.prototypes.QueueChannelWrapper;
import com.amazonaws.services.s3.AmazonS3;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.impl.AMQImpl;

import java.io.IOException;
import java.util.Date;

public class MesureUnit implements Runnable {
    private long startTime;
    private long elapsedTime = 0L;
    private String convertedName;
    private AmazonS3 s3;
    private String bucketName;
    private Channel channel;

    public MesureUnit(String convertedName, AmazonS3 s3, String bucketName, Channel channel) {
        this.convertedName = convertedName;
        this.s3 = s3;
        this.bucketName = bucketName;
        this.channel = channel;
    }

    @Override
    public void run() {
        try {
            mesure();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    private void mesure() throws InterruptedException, IOException {
        startTimer();
        while(true) {
            Thread.sleep(1000);

            if (s3.doesObjectExist(bucketName, convertedName)) {
                System.out.println("Response time for " + convertedName + " is approximate " + getCurrentTime());
                kill();
            } else if(elapsedTime > 180) {
                System.out.println("Did not get the converted file in 3 minutes");
                System.out.println("Closing mesuringUnit");
                kill();
            }
            checkMQQueue();
        }
    }

    private void kill() {
        Thread.currentThread().stop();
    }

    private String getCurrentTime() {
        while (elapsedTime < 2*60*1000) {
            elapsedTime = (new Date()).getTime() - startTime;
        }
        return Long.toString(elapsedTime);
    }

    private void startTimer() {
        startTime = System.currentTimeMillis();
    }

    private void checkMQQueue() throws IOException {
        AMQP.Queue.DeclareOk response = channel.queueDeclarePassive(QueueChannelWrapper.ENCODING_REQUEST_QUEUE);

        System.out.println("The number of videos submited are " + response.getMessageCount());
    }
}


