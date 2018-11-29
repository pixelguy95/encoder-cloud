package client.prototypes;

import aws.CredentialsFetch;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.*;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class CloudWatchPrototype {

    public static String QUEUE_NAME = "all-encoding-queue";

    public static void main(String args[]) throws IOException, TimeoutException, InterruptedException {

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("queue.ndersson.io");
        connectionFactory.setUsername("admin");
        connectionFactory.setPassword("kebabpizza");
        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();

        AmazonCloudWatch cw = AmazonCloudWatchClientBuilder
                .standard()
                .withCredentials(CredentialsFetch.getCredentialsProvider())
                .withRegion(Regions.EU_CENTRAL_1)
                .build();

        AtomicBoolean running = new AtomicBoolean(true);

        while(running.get()) {

            System.out.println("Fetching queue data...");
            AMQP.Queue.DeclareOk ok = channel.queueDeclare(QUEUE_NAME, true, false, false, null);
            double queueSize = ok.getMessageCount();
            double consumerSize = ok.getConsumerCount();

            System.out.println("Queue size: " + queueSize);
            System.out.println("Consumers : " + consumerSize);

            Dimension dimension = new Dimension()
                    .withName("QUEUE")
                    .withValue("ELEMENTS");

            MetricDatum datum = new MetricDatum()
                    .withMetricName("QUEUE_SIZE")
                    .withUnit(StandardUnit.Count)
                    .withValue(queueSize)
                    .withDimensions(dimension);

            PutMetricDataRequest request = new PutMetricDataRequest()
                    .withNamespace("QUEUES")
                    .withMetricData(datum);

            System.out.println("Sending to Amazon CloudWatch...");
            PutMetricDataResult response = cw.putMetricData(request);

            if(response.getSdkHttpMetadata().getHttpStatusCode() == 200) {
                System.out.println("Success");
            }

            System.out.println();



            Thread.sleep(10000);
        }
    }

}
