package infrastructure;

import aws.CredentialsFetch;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsyncClientBuilder;
import com.amazonaws.services.identitymanagement.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;

public class InfrastructureCore {

    public static AWSCredentialsProvider cp;

    public static void main(String args[]) throws IOException {

        cp = CredentialsFetch.getCredentialsProvider();

        System.out.println(createManagerRole());
        //loadManagerPolicy();
        //createManagerPolicy();
        //runManagerInstance();
    }

    public static void loadManagerPolicy() throws IOException {
        AmazonIdentityManagement aim = AmazonIdentityManagementAsyncClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        GetPolicyRequest gpr = new GetPolicyRequest();
        gpr.setPolicyArn("arn:aws:iam::aws:policy/AmazonEC2FullAccess");
        GetPolicyResult res = aim.getPolicy(gpr);
        System.out.println(res.getPolicy().toString());
    }


    /**
     * Creates a new IAM role for the manager
     *
     * Starts by creating the role, then attaches the EC2 policy to the new role
     *
     * @return the arn of the new manager IAM role
     * @throws IOException thrown if you lack the manager assume role document
     */
    public static String createManagerRole() throws IOException {
        AmazonIdentityManagement aim = AmazonIdentityManagementAsyncClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        try {
            CreateRoleRequest crr = new CreateRoleRequest();
            crr.setRoleName("manager-role-v1");
            crr.setDescription("A test for the manager role");
            crr.setAssumeRolePolicyDocument(new String(Files.readAllBytes(Paths.get("iam-policy-json/manager-assume-role-document.json"))));
            aim.createRole(crr);

            AttachRolePolicyRequest arpr = new AttachRolePolicyRequest();
            arpr.setRoleName("manager-role-v1");
            arpr.setPolicyArn("arn:aws:iam::aws:policy/AmazonEC2FullAccess");
            aim.attachRolePolicy(arpr);
        } catch (EntityAlreadyExistsException e) {
            // Thrown if the role has already been created
        }

        GetRoleRequest grr = new GetRoleRequest();
        grr.setRoleName("manager-role-v1");
        return aim.getRole(grr).getRole().getArn();
    }

    public static void runManagerInstance() throws IOException {

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
                .withUserData(Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get("launch-configurations/manager-replica-instance.yml"))))
                .withMinCount(1)
                .withMaxCount(1);
        RunInstancesResult result = ec2Client.runInstances(managerRequest);
        System.out.println(result.getReservation().getReservationId());
    }
}
