package infrastructure.iam;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class InstanceProfileCreator {

    public static InstanceProfile create(AmazonIdentityManagement aim, String roleName, String instanceProfileName, String policyARN) {
        try {
            CreateRoleRequest crr = new CreateRoleRequest();
            crr.setRoleName(roleName);
            crr.setDescription("This is empty right now");
            crr.setAssumeRolePolicyDocument(new String(Files.readAllBytes(Paths.get("iam-policy-json/instance-assume-role-document.json"))));
            aim.createRole(crr);

            CreateInstanceProfileRequest cipr = new CreateInstanceProfileRequest();
            cipr.setInstanceProfileName(instanceProfileName);
            aim.createInstanceProfile(cipr);

            AddRoleToInstanceProfileRequest artipr = new AddRoleToInstanceProfileRequest();
            artipr.setInstanceProfileName(instanceProfileName);
            artipr.setRoleName(roleName);
            aim.addRoleToInstanceProfile(artipr);

            AttachRolePolicyRequest arpr = new AttachRolePolicyRequest();
            arpr.setRoleName(roleName);
            arpr.setPolicyArn(policyARN);
            aim.attachRolePolicy(arpr);
        } catch (EntityAlreadyExistsException e) {
            // Thrown if the role has already been created
        } catch (IOException e) {
            e.printStackTrace();
        }

        GetInstanceProfileRequest gipr = new GetInstanceProfileRequest();
        gipr.setInstanceProfileName(instanceProfileName);
        return aim.getInstanceProfile(gipr).getInstanceProfile();
    }
}
