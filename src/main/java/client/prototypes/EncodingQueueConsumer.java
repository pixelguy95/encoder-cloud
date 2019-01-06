package client.prototypes;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class EncodingQueueConsumer {

    public static String ENCODING_REQUEST_QUEUE = "encoding-request-queue";

    public static void main(String args[]) throws IOException, TimeoutException {

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [x] Received '" + message + "'");
        };

        QueueChannelWrapper channelWrapper = new QueueChannelWrapper("a"); //TODO: FIX
        channelWrapper.channel.basicConsume(ENCODING_REQUEST_QUEUE, true, deliverCallback, consumerTag -> {});
    }



    public static class EncodingConsumer extends DefaultConsumer {

        public EncodingConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            String message = new String(body, "UTF-8");
            System.out.println(message);
        }
    }
}
