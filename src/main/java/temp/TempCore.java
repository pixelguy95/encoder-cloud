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

import java.util.Arrays;
import java.util.List;

public class TempCore {


    public static void main(String args[]) {
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();

        AmazonEC2 ec2Client = AmazonEC2ClientBuilder.standard().withCredentials(cp).withRegion(Regions.EU_CENTRAL_1).build();

        List<Image> images = null;
        try {
            DescribeImagesRequest dir = new DescribeImagesRequest();
            dir.setOwners(Arrays.asList("self"));
            images = ec2Client.describeImages(dir).getImages();
            System.out.println(ec2Client.describeImages(dir).toString());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        System.out.println(images.size());
    }
}
