package client.prototypes;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class LogQueueConsumer {

    public static String ENCODING_REQUEST_QUEUE = "encoding-request-queue";
    public static String BASIC_LOG_QUEUE = "basic-log-queue";

    public static void main(String args[]) throws IOException, TimeoutException {

        QueueChannelWrapper channelWrapper = new QueueChannelWrapper();
        channelWrapper.channel.basicConsume(BASIC_LOG_QUEUE, true, new LogConsumer(channelWrapper.channel));
    }

    public static class LogConsumer extends DefaultConsumer {

        public LogConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
                                   AMQP.BasicProperties properties, byte[] body) throws IOException {
            String message = new String(body, "UTF-8");
            System.out.println(message);
        }
    }
}
