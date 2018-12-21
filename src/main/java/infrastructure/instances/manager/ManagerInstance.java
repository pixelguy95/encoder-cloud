package infrastructure.instances.manager;

import com.amazonaws.services.ec2.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;

public class ManagerInstance extends RunInstancesRequest {

    public ManagerInstance(String managerInstanceProfileARN) throws IOException {
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
        withUserData(Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get("launch-configurations/manager-replica-instance.yml"))));
        withMinCount(1);
        withMaxCount(1);
    }

}
