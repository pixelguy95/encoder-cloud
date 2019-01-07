package encoder;

import aws.CredentialsFetch;
import client.Message;
import client.prototypes.QueueChannelWrapper;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.gson.Gson;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class EncoderConsumer implements DeliverCallback {

    private AmazonS3 amazonS3Client;
    private QueueChannelWrapper queueChannelWrapper;
    private String bucketName;

    public EncoderConsumer(QueueChannelWrapper queueChannelWrapper, String bucketName) {
        amazonS3Client = AmazonS3ClientBuilder.standard().withCredentials(CredentialsFetch.getCredentialsProvider()).withRegion(Regions.EU_CENTRAL_1).build();
        this.queueChannelWrapper = queueChannelWrapper;
        this.bucketName = bucketName;
    }

    @Override
    public void handle(String consumerTag, Delivery message) throws IOException {
        try {
            Gson gson = new Gson();
            String m = new String(message.getBody(), "UTF-8");
            System.out.println(" [x] Received '" + m + "'");

            Message mess = gson.fromJson(m,Message.class);
            convertAndUpload(mess.getKey());
        } finally {

            queueChannelWrapper.channel.basicAck(message.getEnvelope().getDeliveryTag(), false);
        }
    }

    public void convertAndUpload(String fileKeyName) throws IOException {

        File localFile = new File(fileKeyName);
        amazonS3Client.getObject(new GetObjectRequest(bucketName, fileKeyName), localFile); //download unconverted file

        if (localFile.exists() && localFile.canRead()) {

            System.out.println("File successfully downloaded: " + localFile.getAbsolutePath());
            File convertedFile = changeExtension(localFile, ".avi"); // all files are converted to .avi atm

            ProcessBuilder pb = new ProcessBuilder("sudo",
                    "mencoder",
                    localFile.getAbsolutePath(),
                    "-o",
                    convertedFile.getName(),
                    "-oac",
                    "mp3lame",
                    "-ovc",
                    "lavc",
                    "-lavcopts",
                    "vcodec=mpeg1video",
                    "-of",
                    "avi");

            System.out.println("Running command: \n\t" + pb.command().toString());
            Process p = pb.start();
            try {
                System.out.println("Converting file...");
                p.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (p.exitValue() == 0) { // if thread if successful/file is converted
                uploadFileS3(convertedFile);
                localFile.delete();
                convertedFile.delete();
            } else {
                System.out.println("Something went wrong in thread: " + p.toString() + " when converting.");
            }
        }
    }

    private void uploadFileS3(File file) {

        TransferManager tm = TransferManagerBuilder.standard()
                .withS3Client(amazonS3Client)
                .build();

        if (!amazonS3Client.doesObjectExist(bucketName, file.getName())) {

            PutObjectRequest request = new PutObjectRequest(bucketName, file.getName(), new File(file.getPath()));

            request.setGeneralProgressListener(progressEvent -> System.out.println("Transferred bytes: " + progressEvent.getBytesTransferred()));
            Upload upload = tm.upload(request);

            try {
                upload.waitForCompletion();
                System.out.println("Upload " + file.getName() + " successful.");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private File changeExtension(File f, String newExtension) {
        int i = f.getName().lastIndexOf('.');
        String name = f.getName().substring(0, i);
        Path currentRelativePath = Paths.get("");
        return new File(currentRelativePath.toAbsolutePath().toString() + "/" + name + newExtension);
    }
}