package com.github.makewheels.video2022;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RocketMQTest {
    private static DefaultMQProducer producer;

    @BeforeClass
    public static void setup() throws Exception {
        producer = new DefaultMQProducer("test_producer_group");
        producer.setNamesrvAddr("81.70.242.253:9876");
        producer.start();
    }

    @AfterClass
    public static void cleanup() {
        producer.shutdown();
    }

    @Test
    public void testSendMessage() throws Exception {
        Message message = new Message("SELF_TEST_TOPIC", "Hello, RocketMQ!".getBytes());
        producer.send(message);
    }

}
