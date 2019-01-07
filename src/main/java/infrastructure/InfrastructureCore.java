package infrastructure;

import aws.CredentialsFetch;
import client.prototypes.QueueChannelWrapper;
import com.amazonaws.auth.AWSCredentialsProvider;
import infrastructure.cluster.RabbitMQClusterInfrastructure;
import infrastructure.instances.encoder.EncoderInstance;
import infrastructure.instances.manager.ManagerInstance;
import manager.ManagerCore;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class InfrastructureCore {

    public static AWSCredentialsProvider cp;

    public static void main(String args[]) throws IOException, TimeoutException {

        cp = CredentialsFetch.getCredentialsProvider();

        String bucketName = S3BucketSetup.create(cp);
        String queueURL = RabbitMQClusterInfrastructure.create(cp);

        System.out.println("Waiting for cluster to form, if this is your first time running this could take up to 4 mins...");
        waitForClusterToForm(queueURL);
        System.out.println("Cluster is formed");

        ManagerInstance.start(cp, bucketName, queueURL);
        EncoderInstance.start(cp, bucketName, queueURL);

        System.out.println(bucketName);
        System.out.println(queueURL);
    }

    private static void waitForClusterToForm(String queueURL) throws IOException, TimeoutException {
        while(true) {
            try {
                Thread.sleep(5000);
                new QueueChannelWrapper(queueURL);
                break;
            } catch (Exception e) {
            }
        }
    }

}