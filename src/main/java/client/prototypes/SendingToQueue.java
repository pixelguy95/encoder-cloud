package client.prototypes;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class SendingToQueue {

    public static String QUEUE_NAME = "all-encoding-queue";

    public static void main(String args[]) throws IOException, TimeoutException {

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("queue.ndersson.io");
        connectionFactory.setUsername("admin");
        connectionFactory.setPassword("kebabpizza");
        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();

        declareQueue(channel);
        sendMessage("Hello world!", channel);
        shutdown(channel, connection);
    }

    private static void shutdown(Channel channel, Connection connection) throws IOException, TimeoutException {
        channel.close();
        connection.close();
    }

    private static void sendMessage(String s, Channel channel) throws IOException {
        channel.basicPublish("", QUEUE_NAME, null, s.getBytes());
    }

    public static void declareQueue(Channel channel) throws IOException {
        AMQP.Queue.DeclareOk ok = channel.queueDeclare(QUEUE_NAME, true, false, false, null);
    }

}
