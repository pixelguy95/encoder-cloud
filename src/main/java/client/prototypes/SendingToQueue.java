package client.prototypes;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class SendingToQueue {

    public static String ENCODING_REQUEST_QUEUE = "encoding-request-queue";
    public static String BASIC_LOG_QUEUE = "basic-log-queue";

    public static void main(String args[]) throws IOException, TimeoutException {

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("queue.ndersson.io");
        connectionFactory.setUsername("admin");
        connectionFactory.setPassword("kebabpizza");
        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();

        declareQueues(channel);
        sendMessage("Hello world!", channel);

        shutdown(channel, connection);
    }

    private static void shutdown(Channel channel, Connection connection) throws IOException, TimeoutException {
        channel.close();
        connection.close();
    }

    private static void sendMessage(String s, Channel channel) throws IOException {
        channel.basicPublish("", ENCODING_REQUEST_QUEUE, null, s.getBytes());
    }

    public static void declareQueues(Channel channel) throws IOException {
        AMQP.Queue.DeclareOk ok = channel.queueDeclare(ENCODING_REQUEST_QUEUE, true, false, false, null);
        ok = channel.queueDeclare(BASIC_LOG_QUEUE, true, false, false, null);
    }
}
