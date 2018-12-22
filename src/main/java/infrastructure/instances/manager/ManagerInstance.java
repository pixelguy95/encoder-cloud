package infrastructure.instances.manager;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsyncClientBuilder;
import com.amazonaws.services.identitymanagement.model.InstanceProfile;
import infrastructure.iam.InstanceProfileCreator;
import manager.ManagerCore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;

public class ManagerInstance extends RunInstancesRequest {

    public ManagerInstance(String managerInstanceProfileARN) {

        Tag infrastructureTypeTag = new Tag();
        infrastructureTypeTag.setKey("infrastructure-type");
        infrastructureTypeTag.setValue("manager " + System.currentTimeMillis());
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.setResourceType(ResourceType.Instance);
        tagSpecification.setTags(Arrays.asList(infrastructureTypeTag));

        IamInstanceProfileSpecification managerIAM = new IamInstanceProfileSpecification();
        managerIAM.setArn(managerInstanceProfileARN);

        withImageId("ami-0bdf93799014acdc4");
        withKeyName("laptop"); //CJs key, remove later
        withInstanceType(InstanceType.T2Micro);
        withTagSpecifications(tagSpecification);
        withIamInstanceProfile(managerIAM);
        withUserData(Base64.getEncoder().encodeToString(new Scanner(ManagerInstance.class.getResourceAsStream("/manager-replica-instance.yml"), "UTF-8").useDelimiter("\\A").next().getBytes()));
        withMinCount(1);
        withMaxCount(1);
    }

    /**
     * Creates a new IAM role for the manager
     * <p>
     * Starts by creating the role, then attaches the EC2 policy to the new role
     *
     * @return the arn of the new manager IAM role
     * @throws IOException thrown if you lack the manager assume role document
     */
    public static String createManagerRole(AmazonIdentityManagement aim) {
        System.out.println("===Trying to create manager instance profile===");
        InstanceProfile ip = InstanceProfileCreator.create(aim, "manager-role-v1", "manager-iam-instance-profile-v1", "arn:aws:iam::aws:policy/AmazonEC2FullAccess");

        String arn = ip.getArn();
        System.out.println("ARN: " + arn);
        return arn;
    }

    public static void start(AWSCredentialsProvider cp) {
        AmazonIdentityManagement aim = AmazonIdentityManagementAsyncClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        String arn = createManagerRole(aim);

        AmazonEC2 ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        RunInstancesResult result = ec2Client.runInstances(new ManagerInstance(arn));
        System.out.println(result.getReservation().getReservationId());
    }

}
