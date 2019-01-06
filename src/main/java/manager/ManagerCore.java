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
import com.rabbitmq.client.AMQP;
import infrastructure.instances.manager.ManagerInstance;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class ManagerCore implements Runnable {

    public static QueueChannelWrapper qcw;
    private boolean replica;
    private AmazonEC2 ec2Client;

    private QueueChannelWrapper queueWrapper;
    private String queueURL;

    public ManagerCore(String queueURL) throws InterruptedException, IOException, TimeoutException {

        this.queueURL = queueURL;
        queueWrapper = new QueueChannelWrapper(queueURL);
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();

        ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        if(countManagerInstances() > 1) {
            replica = true;
        }

        while(replica) {
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
            ManagerInstance.start(cp, queueURL);
        } catch (Exception e) {
            ManagerCore.log(e.getMessage());
        }

    }

    public int countManagerInstances() {
        AtomicInteger count = new AtomicInteger();
        try {
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
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            AMQP.Queue.DeclareOk ok = null;
            try {
                ok = queueWrapper.channel.queueDeclare(QueueChannelWrapper.ENCODING_REQUEST_QUEUE, true, false, false, null);
                double queueSize = ok.getMessageCount();
                double consumerSize = ok.getConsumerCount();

                log("Encoding queue size: " + queueSize + ", Nr of encoders: " + consumerSize);


            } catch (IOException e) {
                log(e.getMessage());
            }

        }
    }

    public static void main(String args[]) throws IOException, TimeoutException, InterruptedException {
        log("MANAGER STARTING");

        new ManagerCore(args[0]);
    }

    /**
     * Temporary way of publishing log messages
     * @param message message will be written to the basic log queue
     */
    public static void log(String message) {
        try {

            String identity = "[unknown]";
            try{
                identity = "[" + EC2MetadataUtils.getInstanceId() + "]";
            } catch (Exception e) {

            }

            message = identity + " " + message;
            qcw.channel.basicPublish("", QueueChannelWrapper.BASIC_LOG_QUEUE, null, message.getBytes());
        } catch (IOException e) {
        }
    }
}
