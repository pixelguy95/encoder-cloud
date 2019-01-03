package infrastructure;

import aws.CredentialsFetch;
import com.amazonaws.auth.AWSCredentialsProvider;
import infrastructure.instances.manager.ManagerInstance;
import manager.ManagerCore;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class InfrastructureCore {

    public static AWSCredentialsProvider cp;

    public static void main(String args[]) throws IOException, TimeoutException {
        ManagerCore.createQueueWrapper();
        cp = CredentialsFetch.getCredentialsProvider();
        ManagerInstance.start(cp);
    }

}
