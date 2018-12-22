package manager;

import aws.CredentialsFetch;
import client.prototypes.QueueChannelWrapper;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.util.EC2MetadataUtils;
import infrastructure.instances.manager.ManagerInstance;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class ManagerCore implements Runnable {

    public static QueueChannelWrapper qcw;
    private boolean replica;
    private AmazonEC2 ec2Client;

    public ManagerCore() throws InterruptedException {
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();

        ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        if(countManagerInstances() > 0) {
            replica = true;
        }

        log("is replica : " + replica);

        while(replica) {
            Thread.sleep(5000);
            if(countManagerInstances() == 1) {
                replica = false;
            }
        }

        startReplica();

        new Thread(this).start();
    }

    private void startReplica() {
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();
        ManagerInstance.start(cp);
    }

    public int countManagerInstances() {
        AtomicInteger count = new AtomicInteger();
        try {
            //TODO: replica if there already is a manager tagged instance running
            this.replica = false;
            ec2Client.describeInstances().getReservations()
                    .forEach(reservation-> reservation.getInstances()
                            .forEach(instance -> instance.getTags().forEach(tag -> {
                                if(tag.getKey().startsWith("manager"))
                                {
                                    count.getAndIncrement();
                                }
                            })));


        } catch (Exception e) {
            log(e.getMessage());
        }

        return count.get();
    }

    @Override
    public void run() {
        while(true) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            log("[" + EC2MetadataUtils.getInstanceId() + "] I am manager I am alive");
        }
    }

    public static void main(String args[]) throws IOException, TimeoutException, InterruptedException {

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
