package manager;

import aws.CredentialsFetch;
import client.prototypes.QueueChannelWrapper;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.InstanceState;
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

        if(countManagerInstances() > 1) {
            replica = true;
        }

        log("is replica : " + replica);

        while(replica) {
            log("Im inside!");
            Thread.sleep(5000);
            if(countManagerInstances() == 1) {
                log("manager must have died: " + countManagerInstances());
                replica = false;
            }
        }

        log("starting replica");
        startReplica();
        log("replica started");

        new Thread(this).start();
    }

    private void startReplica() {
        try {
            AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();
            ManagerInstance.start(cp);
        } catch (Exception e) {
            ManagerCore.log(e.getMessage());
        }

    }

    public int countManagerInstances() {
        AtomicInteger count = new AtomicInteger();
        try {
            //TODO: replica if there already is a manager tagged instance running
            this.replica = false;
            ec2Client.describeInstances().getReservations()
                    .forEach(reservation-> reservation.getInstances().stream().filter(i->i.getState().getCode() == 16)
                            .forEach(instance -> instance.getTags().forEach(tag -> {
                                if(tag.getValue().startsWith("manager"))
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

        createQueueWrapper();
        log("MANAGER STARTING");

        new ManagerCore();
    }

    public static void createQueueWrapper() throws IOException, TimeoutException {
        qcw = new QueueChannelWrapper();
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
