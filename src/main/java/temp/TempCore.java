package temp;

import aws.CredentialsFetch;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.util.EC2MetadataUtils;
import infrastructure.S3BucketSetup;
import infrastructure.cluster.RabbitMQClusterInfrastructure;
import infrastructure.instances.encoder.EncoderInstance;
import infrastructure.instances.manager.ManagerInstance;

import java.util.Arrays;
import java.util.List;

public class TempCore {


    public static void main(String args[]) {
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();

        AmazonEC2 ec2Client = AmazonEC2ClientBuilder.standard().withCredentials(cp).withRegion(Regions.EU_CENTRAL_1).build();

        ManagerInstance.start(cp, "encoder-bucket-hujb2klcx0ab5p3r6vjkpzsacobze-dzc92ard21ycq", "rabbitmq-cluster-loadbalancer-528595232.eu-central-1.elb.amazonaws.com");
    }
}
