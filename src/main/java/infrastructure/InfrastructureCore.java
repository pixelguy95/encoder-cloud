package infrastructure;

import aws.CredentialsFetch;
import com.amazonaws.auth.AWSCredentialsProvider;
import infrastructure.cluster.RabbitMQClusterInfrastructure;
import infrastructure.instances.encoder.EncoderInstance;
import infrastructure.instances.manager.ManagerInstance;
import manager.ManagerCore;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class InfrastructureCore {

    public static AWSCredentialsProvider cp;

    public static void main(String args[]) {

        cp = CredentialsFetch.getCredentialsProvider();

        String bucketName = S3BucketSetup.create(cp);
        String queueURL = RabbitMQClusterInfrastructure.create(cp);
        ManagerInstance.start(cp);
        EncoderInstance.start(cp, bucketName, queueURL);
    }

}