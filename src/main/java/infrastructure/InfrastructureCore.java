package infrastructure;

import aws.CredentialsFetch;
import client.prototypes.QueueChannelWrapper;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.CreateKeyPairRequest;
import com.amazonaws.services.ec2.model.CreateKeyPairResult;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import com.amazonaws.services.ec2.model.KeyPairInfo;
import com.sun.org.apache.xpath.internal.operations.Bool;
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

        AmazonEC2 ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        System.out.println("Creating key-pair for ec2");
        createKeyPair(ec2Client);


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
                System.out.println("Not done, trying again...");
                new QueueChannelWrapper(queueURL);
                System.out.println("QueueChannelWrapper Done");
                break;
            } catch (Exception e) {
            }
        }
    }

    private static void createKeyPair(AmazonEC2 ec2Client) {

        boolean hasKey_pair = false;

        DescribeKeyPairsResult response = ec2Client.describeKeyPairs();

        for(KeyPairInfo key_pair : response.getKeyPairs()) {
            if(key_pair.getKeyName().equals("school")){
                hasKey_pair = true;
            }
        }

        if(!hasKey_pair) {
            CreateKeyPairRequest request = new CreateKeyPairRequest().withKeyName("school");
            ec2Client.createKeyPair(request);
        }
    }
}