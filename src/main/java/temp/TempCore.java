package temp;

import aws.CredentialsFetch;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import infrastructure.instances.manager.ManagerInstance;

public class TempCore {


    public static void main(String args[]) {
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();

        AmazonEC2 ec2Client = AmazonEC2ClientBuilder.standard().withCredentials(cp).withRegion(Regions.EU_CENTRAL_1).build();

        ManagerInstance.start(cp, "encoder-bucket-rmhplccfeqxyqyqckqkhp3lz7f9ueefadqrfrh7ji", "rabbitmq-cluster-loadbalancer-528595232.eu-central-1.elb.amazonaws.com");
    }
}
