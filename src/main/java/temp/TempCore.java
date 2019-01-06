package temp;

import aws.CredentialsFetch;
import com.amazonaws.auth.AWSCredentialsProvider;
import infrastructure.S3BucketSetup;
import infrastructure.cluster.RabbitMQClusterInfrastructure;
import infrastructure.instances.encoder.EncoderInstance;

public class TempCore {


    public static void main(String args[]) {
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();
        //S3BucketSetup.create(cp);

        EncoderInstance.start(cp, "temporary-encoder-bucket-to-be-deleted", "rabbitmq-cluster-loadbalancer-449114661.eu-central-1.elb.amazonaws.com");
    }
}
