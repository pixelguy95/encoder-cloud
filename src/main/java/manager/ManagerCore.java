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

public class ManagerCore implements Runnable {

    public static QueueChannelWrapper qcw;
    private boolean replica;
    private AmazonEC2 ec2Client;

    public ManagerCore() {
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();



        ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        try {
            //TODO: replica if there already is a manager tagged instance running
            this.replica = false;
            ec2Client.describeInstances().getReservations()
                    .forEach(reservation-> reservation.getInstances()
                            .forEach(instance -> instance.getTags().forEach(tag -> {
                                if(tag.getKey().startsWith("manager"))
                                {
                                    replica = true;
                                }
                            })));

            log("Is replica : " + replica);
        } catch (Exception e) {
            log(e.getMessage());
        }

        //TODO: if master, start replica
        //TODO: if replica wait for master to die

        if(!replica) {

        }
    }

    @Override
    public void run() {

    }

    public static void main(String args[]) throws IOException, TimeoutException {

        qcw = new QueueChannelWrapper();
        log("MANAGER STARTING");

        new ManagerCore();

        qcw.shutdown();
    }

    /**
     * Temporary way of publishing log messages
     * @param message message will be written to the basic log queue
     */
    public static void log(String message) {
        try {
            qcw.channel.basicPublish("", QueueChannelWrapper.BASIC_LOG_QUEUE, null, message.getBytes());
        } catch (IOException e) {
        }
    }
}
