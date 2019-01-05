package infrastructure.iam;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.*;
import infrastructure.instances.manager.ManagerInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

public class InstanceProfileCreator {

    public static InstanceProfile create(AmazonIdentityManagement aim, String roleName, String instanceProfileName, List<String> policyARNs) {
        try {
            CreateRoleRequest crr = new CreateRoleRequest();
            crr.setRoleName(roleName);
            crr.setDescription("This is empty right now");

            String s = new Scanner(ManagerInstance.class.getResourceAsStream("/instance-assume-role-document.json"), "UTF-8").useDelimiter("\\A").next();
            crr.setAssumeRolePolicyDocument(s);
            aim.createRole(crr);

            for(String policyARN : policyARNs) {
                AttachRolePolicyRequest arpr = new AttachRolePolicyRequest();
                arpr.setRoleName(roleName);
                arpr.setPolicyArn(policyARN);
                aim.attachRolePolicy(arpr);
            }

            Thread.sleep(5000);

            DeleteInstanceProfileRequest dipr = new DeleteInstanceProfileRequest();
            dipr.setInstanceProfileName(instanceProfileName);
            aim.deleteInstanceProfile(dipr);

            Thread.sleep(5000);
            CreateInstanceProfileRequest cipr = new CreateInstanceProfileRequest();
            cipr.setInstanceProfileName(instanceProfileName);
            aim.createInstanceProfile(cipr);

            Thread.sleep(5000);
            AddRoleToInstanceProfileRequest artipr = new AddRoleToInstanceProfileRequest();
            artipr.setInstanceProfileName(instanceProfileName);
            artipr.setRoleName(roleName);
            aim.addRoleToInstanceProfile(artipr);

            Thread.sleep(5000);
        } catch (EntityAlreadyExistsException e) {
            System.out.println(e.getMessage());
        } catch (Exception e) {
            System.out.println("Error");
            System.out.println(e.getClass().getName());
            System.out.println(e.getMessage());
            System.exit(0);
        }

        GetInstanceProfileRequest gipr = new GetInstanceProfileRequest();
        gipr.setInstanceProfileName(instanceProfileName);
        return aim.getInstanceProfile(gipr).getInstanceProfile();
    }
}
