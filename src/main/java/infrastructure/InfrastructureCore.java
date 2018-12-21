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
import infrastructure.iam.InstanceProfileCreator;
import infrastructure.instances.manager.ManagerInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;

public class InfrastructureCore {

    public static AWSCredentialsProvider cp;

    public static void main(String args[]) throws IOException {

        cp = CredentialsFetch.getCredentialsProvider();

        System.out.println("Creating manager IAM role");
        String managerIAMRoleARN = createManagerRole();
        System.out.println("Starting initial manager instance");
        runManagerInstance(managerIAMRoleARN);
    }

    /**
     * Creates a new IAM role for the manager
     *
     * Starts by creating the role, then attaches the EC2 policy to the new role
     *
     * @return the arn of the new manager IAM role
     * @throws IOException thrown if you lack the manager assume role document
     */
    public static String createManagerRole() {
        AmazonIdentityManagement aim = AmazonIdentityManagementAsyncClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        System.out.println("===Trying to create manager instance profile===");
        InstanceProfile ip = InstanceProfileCreator.create(aim, "manager-role-v1", "manager-iam-instance-profile-v1", "arn:aws:iam::aws:policy/AmazonEC2FullAccess");

        String arn = ip.getArn();
        System.out.println("ARN: " + arn);
        return arn;
    }

    public static void runManagerInstance(String IAMRole) throws IOException {

        AmazonEC2 ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        RunInstancesResult result = ec2Client.runInstances(new ManagerInstance(IAMRole));
        System.out.println(result.getReservation().getReservationId());
    }
}
