package infrastructure;

import aws.CredentialsFetch;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;

public class InfrastructureCore {

    public static void main(String args[]) throws IOException {

        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();
        AmazonEC2 ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();


        Tag infrastructureTypeTag = new Tag();
        infrastructureTypeTag.setKey("infrastructure-type");
        infrastructureTypeTag.setValue("manager " + System.currentTimeMillis());
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.setResourceType(ResourceType.Instance);
        tagSpecification.setTags(Arrays.asList(infrastructureTypeTag));



        //Start manager instance
        RunInstancesRequest managerRequest = new RunInstancesRequest()
                .withImageId("ami-0bdf93799014acdc4")
                .withKeyName("school") //CJs key
                .withInstanceType(InstanceType.T2Micro)
                .withTagSpecifications(tagSpecification)
                .withUserData(Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get("launch-configurations/manager-replica-instance.yml"))));
        ec2Client.runInstances(managerRequest);
    }
}
