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
import java.util.concurrent.TimeoutException;


public class ClientCore {

    private AmazonS3 s3;
    private QueueChannelWrapper channelWrapper;


    public ClientCore(String bucketName, String queueURL, String filePath) throws IOException, TimeoutException {

        s3 = AmazonS3ClientBuilder.standard().withCredentials(CredentialsFetch.getCredentialsProvider()).
                withRegion(Regions.EU_CENTRAL_1).build();

        channelWrapper = new QueueChannelWrapper(queueURL);

        for(int i = 0; i < 16; i++) {
            upload(bucketName, new File(filePath), (i)+".mp4");
            sendMessage((i)+".mp4", channelWrapper.channel);
        }
       // channelWrapper.shutdown();

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


    public static void main(String[] args) throws IOException, TimeoutException {

        if (args.length < 3) {
            System.out.println("-------------Invalid input-------------\n" +
                    "Expected args: [bucketName] [queueURL] [fileToConvert/path]");
            System.exit(0);
        }

        new ClientCore(args[0], args[1], args[2]);
    }
}
