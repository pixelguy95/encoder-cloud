package encoder;

import client.Message;
import client.prototypes.QueueChannelWrapper;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.google.gson.Gson;
import com.rabbitmq.client.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class EncoderCore {

    private BasicAWSCredentials awsCredentials = new BasicAWSCredentials(/*put pub key here*/null,null/*put private key here*/);
    private AmazonS3Client s3Client = new AmazonS3Client(awsCredentials);

   Channel channel;
   Connection connection;

    public static String ENCODING_REQUEST_QUEUE = "encoding-request-queue";
    private String bucket_name = "kgencoderbucket";

    public EncoderCore() {


    }

    public File getFile() throws URISyntaxException {

        return new File(EncoderCore.class.getResource("/sample_video.mp4").toURI());

    }


    public List<S3ObjectSummary> getFilesFromS3() {

        ListObjectsV2Result result = s3Client.listObjectsV2(bucket_name);
        return result.getObjectSummaries();
    }

    public void getFileFromS3(String key_name) {

        S3Object o = s3Client.getObject(bucket_name, key_name);
        S3ObjectInputStream s3is = o.getObjectContent();

        //This is where the downloaded file will be saved
        File localFile = new File("/home/c14/c14nbn/ddd.mp4");

        //This returns an ObjectMetadata file but you don't have to use this if you don't want
        ObjectMetadata object = s3Client.getObject(new GetObjectRequest(bucket_name, "converted/sample_video.mp4"), localFile);

        if (localFile.exists() && localFile.canRead()) {
            System.out.println("IT WORK!!!");
        }
    }


    public void initConnection() {


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


    public static void main(String[] args) {


        EncoderCore core = new EncoderCore();
        core.initConnection();

//        for (S3ObjectSummary list : core.getFilesFromS3()) {
//
//            System.out.println(list);
//        }

        try {


            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                Gson gson = new Gson();
                Message message = gson.fromJson(new String(delivery.getBody(), "UTF-8"), Message.class);
                System.out.println("BODY: " + new String(delivery.getBody()));
                System.out.println(message.toString());
            };

            core.channel.basicConsume(ENCODING_REQUEST_QUEUE, true, deliverCallback, consumerTag -> {});

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
