package client.prototypes;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class QueueChannelWrapper {

    public static String ENCODING_REQUEST_QUEUE =   "encoding-request-queue";
    public static String BASIC_LOG_QUEUE =          "basic-log-queue";

    public Channel channel;
    public Connection connection;

    public QueueChannelWrapper() throws IOException, TimeoutException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("queue.ndersson.io");
        connectionFactory.setUsername("admin");
        connectionFactory.setPassword("kebabpizza");
        connection = connectionFactory.newConnection();
        channel = connection.createChannel();

        declareQueues(channel);
    }

    private void declareQueues(Channel channel) throws IOException {
        AMQP.Queue.DeclareOk ok = channel.queueDeclare(ENCODING_REQUEST_QUEUE, true, false, false, null);
        ok = channel.queueDeclare(BASIC_LOG_QUEUE, true, false, false, null);
    }

    public void shutdown() throws IOException, TimeoutException {
        channel.close();
        connection.close();
    }
}
