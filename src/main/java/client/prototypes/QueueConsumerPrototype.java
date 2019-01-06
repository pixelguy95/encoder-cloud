package client.prototypes;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class QueueConsumerPrototype {

//    public static String ENCODING_REQUEST_QUEUE = "encoding-request-queue";
//    public static String BASIC_LOG_QUEUE = "basic-log-queue";
//
//    public static void main(String args[]) throws IOException, TimeoutException {
//
//        QueueChannelWrapper channelWrapper = new QueueChannelWrapper();
//        channelWrapper.channel.basicConsume(ENCODING_REQUEST_QUEUE, true, new EncoderConsumer(channelWrapper.channel));
//    }
//
//    public static class EncoderConsumer extends DefaultConsumer {
//
//        /**
//         * Constructs a new instance and records its association to the passed-in channel.
//         *
//         * @param channel the channel to which this consumer is attached
//         */
//        public EncoderConsumer(Channel channel) {
//            super(channel);
//        }
//
//
//        @Override
//        public void handleDelivery(String consumerTag, Envelope envelope,
//                                   AMQP.BasicProperties properties, byte[] body) throws IOException {
//            String message = "[LOG] " + new String(body, "UTF-8");
//            getChannel().basicPublish("", BASIC_LOG_QUEUE, null, message.getBytes());
//        }
//    }
}
