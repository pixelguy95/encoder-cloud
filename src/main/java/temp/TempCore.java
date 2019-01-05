package temp;

import aws.CredentialsFetch;
import com.amazonaws.auth.AWSCredentialsProvider;
import infrastructure.cluster.RabbitMQClusterInfrastructure;

public class TempCore {


    public static void main(String args[]) {
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();
        RabbitMQClusterInfrastructure.create(cp);
    }
}
