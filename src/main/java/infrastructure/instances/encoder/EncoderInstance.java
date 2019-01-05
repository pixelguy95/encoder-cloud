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
import manager.ManagerCore;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;

public class EncoderInstance extends RunInstancesRequest {

    public EncoderInstance(String encoderInstanceProfileARN) {
        Tag infrastructureTypeTag = new Tag();
        infrastructureTypeTag.setKey("infrastructure-type");
        infrastructureTypeTag.setValue("manager " + System.currentTimeMillis());
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.setResourceType(ResourceType.Instance);
        tagSpecification.setTags(Arrays.asList(infrastructureTypeTag));

        IamInstanceProfileSpecification managerIAM = new IamInstanceProfileSpecification();
        managerIAM.setArn(encoderInstanceProfileARN);

        withImageId("ami-0bdf93799014acdc4");
        withKeyName("my-key-pair"); //KGs key, remove later
        withInstanceType(InstanceType.T2Micro);
        withTagSpecifications(tagSpecification);
        withIamInstanceProfile(managerIAM);
        withUserData(Base64.getEncoder().encodeToString(new Scanner(ManagerInstance.class.getResourceAsStream("/encoder-instance.yml"), "UTF-8").useDelimiter("\\A").next().getBytes()));
        withMinCount(1);
        withMaxCount(1);
    }

    public static String createEncoderRole(AmazonIdentityManagement aim) {
        System.out.println("===Trying to create manager instance profile===");
        List<String> policyARNs = Arrays.asList(
                "arn:aws:iam::013636191514:role/encoder-role-v1 ");
        InstanceProfile ip = InstanceProfileCreator.create(aim, "encoder-role-v1", "encoder-iam-instance-profile-v1", policyARNs);

        String arn = ip.getArn();
        System.out.println("ARN: " + arn);
        return arn;
    }

    public static void start(AWSCredentialsProvider cp) {
        AmazonIdentityManagement aim = AmazonIdentityManagementAsyncClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        try{
            createEncoderRole(aim);
        } catch (Exception e) {
            EncoderCore.log(e.getMessage());
        }

        EncoderCore.log("I failed here 1");

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        EncoderCore.log("I failed here 2");

        try {
            EncoderCore.log("I failed here 3");
            GetInstanceProfileRequest gipr = new GetInstanceProfileRequest();
            EncoderCore.log("I failed here 4");
            gipr.setInstanceProfileName("manager-iam-instance-profile-v1");
            EncoderCore.log("I failed here 5");
            String arn = aim.getInstanceProfile(gipr).getInstanceProfile().getArn();
            EncoderCore.log("I failed here 6");

            AmazonEC2 ec2Client = AmazonEC2ClientBuilder.standard()
                    .withRegion(Regions.EU_CENTRAL_1)
                    .withCredentials(cp)
                    .build();

            EncoderCore.log("I failed here 7 " + arn);

            RunInstancesResult result = ec2Client.runInstances(new EncoderInstance(arn));
            EncoderCore.log("I failed here 8");

            System.out.println(result.getReservation().getReservationId());
        } catch (Exception e) {
            EncoderCore.log(e.getMessage());
        }

        EncoderCore.log("I failed here end");
    }
}
