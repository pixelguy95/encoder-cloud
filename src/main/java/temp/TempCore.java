package temp;

import aws.CredentialsFetch;
import com.amazonaws.auth.AWSCredentialsProvider;
import infrastructure.loadbalancer.RabbitMQLoadBalancer;

public class TempCore {


    public static void main(String args[]) {
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();
        RabbitMQLoadBalancer.create(cp);
    }
}
