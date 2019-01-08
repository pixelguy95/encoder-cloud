package client;

import client.prototypes.QueueChannelWrapper;
import com.amazonaws.services.s3.AmazonS3;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.impl.AMQImpl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class MesureUnit implements Runnable {
    private long startTime;
    private long elapsedTime = 0L;
    private String convertedName;
    private AmazonS3 s3;
    private String bucketName;

    public MesureUnit(String convertedName, AmazonS3 s3, String bucketName) {
        this.convertedName = convertedName;
        this.s3 = s3;
        this.bucketName = bucketName;
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
            Thread.sleep(100);
            if (s3.doesObjectExist(bucketName, convertedName)) {
                //System.out.println("Response time for " + convertedName + " is approximate " + getCurrentTime() + " milliseconds");
                System.out.println(getCurrentTime());
                kill();
            } else if(elapsedTime > 180000) {
                System.out.println("Did not get the converted file in 3 minutes");
                System.out.println("Closing mesuringUnit");
                kill();
            }
        }
    }

    private void kill() {
        Thread.currentThread().stop();
    }

    private String getCurrentTime() {
        elapsedTime = new Date().getTime() - startTime;
        return Long.toString(elapsedTime );
    }

    private void startTimer() {
        startTime = System.currentTimeMillis();
    }

}


