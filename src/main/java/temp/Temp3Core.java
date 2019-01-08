package temp;

import aws.CredentialsFetch;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import infrastructure.instances.encoder.EncoderInstance;
import infrastructure.instances.manager.ManagerInstance;

public class Temp3Core {

    private static AmazonEC2 ec2Client;

    public static void main(String args[]) {
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();

        ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        ManagerInstance.start(cp, "encoder-bucket-eyfzgpfavfxzpv5eigihs8ofeu9vm40rztrgkgzgr0k", "rabbitmq-cluster-loadbalancer-528595232.eu-central-1.elb.amazonaws.com");

    }

}
