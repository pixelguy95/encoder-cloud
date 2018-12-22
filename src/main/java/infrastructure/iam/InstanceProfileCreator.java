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

            AttachRolePolicyRequest arpr = new AttachRolePolicyRequest();
            arpr.setRoleName(roleName);
            arpr.setPolicyArn(policyARN);
            aim.attachRolePolicy(arpr);

            DeleteInstanceProfileRequest dipr = new DeleteInstanceProfileRequest();
            dipr.setInstanceProfileName(instanceProfileName);
            aim.deleteInstanceProfile(dipr);

            CreateInstanceProfileRequest cipr = new CreateInstanceProfileRequest();
            cipr.setInstanceProfileName(instanceProfileName);
            aim.createInstanceProfile(cipr);

            AddRoleToInstanceProfileRequest artipr = new AddRoleToInstanceProfileRequest();
            artipr.setInstanceProfileName(instanceProfileName);
            artipr.setRoleName(roleName);
            aim.addRoleToInstanceProfile(artipr);

        } catch (EntityAlreadyExistsException e) {
            System.out.println(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            
        }

        GetInstanceProfileRequest gipr = new GetInstanceProfileRequest();
        gipr.setInstanceProfileName(instanceProfileName);
        return aim.getInstanceProfile(gipr).getInstanceProfile();
    }
}
