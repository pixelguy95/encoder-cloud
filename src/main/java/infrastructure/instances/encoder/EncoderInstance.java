package infrastructure.instances.encoder;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsyncClientBuilder;
import com.amazonaws.services.identitymanagement.model.GetInstanceProfileRequest;
import com.amazonaws.services.identitymanagement.model.InstanceProfile;
import encoder.EncoderCore;
import infrastructure.iam.InstanceProfileCreator;
import infrastructure.instances.manager.ManagerInstance;
import infrastructure.securitygroup.SecurityGroupCreator;
import manager.ManagerCore;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;

public class EncoderInstance extends RunInstancesRequest {

    public EncoderInstance(String imageID, String encoderInstanceProfileARN, SecurityGroup sg, String bucketName, String queueURL) {
        Tag infrastructureTypeTag = new Tag();
        infrastructureTypeTag.setKey("infrastructure-type");
        infrastructureTypeTag.setValue("encoder " + System.currentTimeMillis());
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.setResourceType(ResourceType.Instance);
        tagSpecification.setTags(Arrays.asList(infrastructureTypeTag));

        IamInstanceProfileSpecification encoderIAM = new IamInstanceProfileSpecification();
        encoderIAM.setArn(encoderInstanceProfileARN);

        withImageId(imageID);
        withKeyName("school");
        withInstanceType(InstanceType.T2Micro);
        withTagSpecifications(tagSpecification);
        withIamInstanceProfile(encoderIAM);
        withMinCount(1);
        withMaxCount(1);
        withSecurityGroupIds(sg.getGroupId());
    }

    public EncoderInstance(String encoderInstanceProfileARN, SecurityGroup sg, String bucketName, String queueURL) {
        Tag infrastructureTypeTag = new Tag();
        infrastructureTypeTag.setKey("infrastructure-type");
        infrastructureTypeTag.setValue("encoder " + System.currentTimeMillis());
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.setResourceType(ResourceType.Instance);
        tagSpecification.setTags(Arrays.asList(infrastructureTypeTag));

        IamInstanceProfileSpecification encoderIAM = new IamInstanceProfileSpecification();
        encoderIAM.setArn(encoderInstanceProfileARN);

        withImageId("ami-0bdf93799014acdc4");
        withKeyName("school");
        withInstanceType(InstanceType.T2Micro);
        withTagSpecifications(tagSpecification);
        withIamInstanceProfile(encoderIAM);
        String launchConfigContents = new Scanner(EncoderInstance.class.getResourceAsStream("/encoder-instance.yml"), "UTF-8").useDelimiter("\\A").next();
        launchConfigContents = launchConfigContents.replaceAll("%BUCKETNAME%", bucketName);
        launchConfigContents = launchConfigContents.replaceAll("%QUEUEURL%", queueURL);
        withUserData(Base64.getEncoder().encodeToString(launchConfigContents.getBytes()));
        withMinCount(1);
        withMaxCount(1);
        withSecurityGroupIds(sg.getGroupId());
    }

    public static String createEncoderRole(AmazonIdentityManagement aim) {
        System.out.println("===Trying to create manager instance profile===");
        List<String> policyARNs = Arrays.asList(
                "arn:aws:iam::aws:policy/AmazonS3FullAccess");
        InstanceProfile ip = InstanceProfileCreator.create(aim, "encoder-role-v1", "encoder-iam-instance-profile-v1", policyARNs);

        String arn = ip.getArn();
        System.out.println("ARN: " + arn);
        return arn;
    }

    public static void start(String imageID, AWSCredentialsProvider cp, String bucketName, String queueURL) {
        AmazonIdentityManagement aim = AmazonIdentityManagementAsyncClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        try{
            createEncoderRole(aim);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            GetInstanceProfileRequest gipr = new GetInstanceProfileRequest();
            gipr.setInstanceProfileName("encoder-iam-instance-profile-v1");
            String arn = aim.getInstanceProfile(gipr).getInstanceProfile().getArn();

            AmazonEC2 ec2Client = AmazonEC2ClientBuilder.standard()
                    .withRegion(Regions.EU_CENTRAL_1)
                    .withCredentials(cp)
                    .build();

            SecurityGroup sg = SecurityGroupCreator.create(ec2Client, "encoder-security-group", Arrays.asList(new SecurityGroupCreator.PortRange(22, 22)));

            RunInstancesResult result = ec2Client.runInstances(new EncoderInstance(imageID, arn, sg, bucketName, queueURL));

            System.out.println(result.getReservation().getReservationId());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public static void start(AWSCredentialsProvider cp, String bucketName, String queueURL) {
        AmazonIdentityManagement aim = AmazonIdentityManagementAsyncClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        try{
            createEncoderRole(aim);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            GetInstanceProfileRequest gipr = new GetInstanceProfileRequest();
            gipr.setInstanceProfileName("encoder-iam-instance-profile-v1");
            String arn = aim.getInstanceProfile(gipr).getInstanceProfile().getArn();

            AmazonEC2 ec2Client = AmazonEC2ClientBuilder.standard()
                    .withRegion(Regions.EU_CENTRAL_1)
                    .withCredentials(cp)
                    .build();

            SecurityGroup sg = SecurityGroupCreator.create(ec2Client, "encoder-security-group", Arrays.asList(new SecurityGroupCreator.PortRange(22, 22)));

            RunInstancesResult result = ec2Client.runInstances(new EncoderInstance(arn, sg, bucketName, queueURL));

            System.out.println(result.getReservation().getReservationId());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
