package temp;

import aws.CredentialsFetch;
import com.amazonaws.auth.AWSCredentialsProvider;
import infrastructure.S3BucketSetup;
import infrastructure.cluster.RabbitMQClusterInfrastructure;

public class TempCore {


    public static void main(String args[]) {
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();
        S3BucketSetup.create(cp);
    }
}
