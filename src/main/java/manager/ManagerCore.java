package manager;

import aws.CredentialsFetch;
import client.prototypes.QueueChannelWrapper;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsyncClientBuilder;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class ManagerCore {

    private boolean replica;
    private AmazonEC2 ec2Client;

    public ManagerCore(boolean replica) {
        this.replica = replica;
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();

        ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        System.out.println("Number of instances");
        ec2Client.describeInstances().getReservations().forEach(i-> System.out.println(i.getInstances().size()));

        //TODO: if master, start replica
        //TODO: if replica wait for master to die

        if(!replica) {

        }
    }

    public static void main(String args[]) throws IOException, TimeoutException {

        QueueChannelWrapper qcw = new QueueChannelWrapper();
        qcw.channel.basicPublish("", QueueChannelWrapper.BASIC_LOG_QUEUE, null, "MANAGER STARTING".getBytes());

        qcw.shutdown();

        if(args.length > 0 && args[0].equals("r")) {
            new ManagerCore(true);
        } else {
            new ManagerCore(false);
        }


    }
}
