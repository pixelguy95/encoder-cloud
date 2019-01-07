package client;

import aws.CredentialsFetch;
import client.prototypes.QueueChannelWrapper;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.gson.Gson;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;


public class ClientCore implements Runnable{

    private AmazonS3 s3;
    private QueueChannelWrapper channelWrapper;
    private String bucketName;
    private String queueURL;
    private String filePath;
    private int time;


    public ClientCore(String bucketName, String queueURL, String filePath, int time) throws IOException, TimeoutException {

        this.bucketName = bucketName;
        this.queueURL = queueURL;
        this.filePath = filePath;
        this.time = time;

    }

    private void upload(String bucketName, File file, String s3name) {

        TransferManager tm = TransferManagerBuilder.standard()
                .withS3Client(s3)
                .build();

        PutObjectRequest request = new PutObjectRequest(bucketName, s3name, new File(file.getPath()));

        request.setGeneralProgressListener(progressEvent ->
                System.out.println("Transferred bytes: " + progressEvent.getBytesTransferred() + "/" + file.length()));
        Upload upload = tm.upload(request);

        try {
            upload.waitForCompletion();

        } catch (AmazonServiceException e) {
            System.err.println(e.getErrorMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Success! Uploaded: " + s3name + " to bucket " + bucketName);
    }



    private void sendMessage(String s3Name, Channel channel) throws IOException {
        channel.basicPublish("", QueueChannelWrapper.ENCODING_REQUEST_QUEUE, null, s3Name.getBytes());
        System.out.println("was ist das: " + s3Name.getBytes());

    }

    @Override
    public void run() {

        s3 = AmazonS3ClientBuilder.standard().withCredentials(CredentialsFetch.getCredentialsProvider()).
                withRegion(Regions.EU_CENTRAL_1).build();

        try {
            channelWrapper = new QueueChannelWrapper(queueURL);
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }

        Timer timer = new Timer();


        timer.schedule(new TimerTask() {
            int counter = 0;
            @Override
            public void run() {
                upload(bucketName, new File(filePath), Thread.currentThread().getName()+ counter +".mp4");

                try {
                    sendMessage(Thread.currentThread().getName()+ counter +".mp4", channelWrapper.channel);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                counter++;
            }
        },Integer.toUnsignedLong(time));
    }
}
