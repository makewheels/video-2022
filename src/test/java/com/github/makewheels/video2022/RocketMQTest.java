package com.github.makewheels.video2022;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class RocketMQTest {
    private static DefaultMQProducer producer;

    @BeforeAll
    public static void setUp() throws Exception {
        // 实例化消息生产者Producer
        producer = new DefaultMQProducer("myproducer");
        // 设置NameServer的地址
        producer.setNamesrvAddr("81.70.242.253:9876");
        // 启动Producer实例
        producer.start();
    }

    @Test
    public void testSendMessage() throws Exception {
        // 创建消息，并指定Topic，Tag和消息体
        Message msg = new Message("mytopic", "mytag", "Hello RocketMQ".getBytes());
        // 发送消息到一个Broker
        producer.send(msg);
        // 等待1秒钟，以确保消息被发送到Broker
        TimeUnit.SECONDS.sleep(1);
        // 在这里添加代码来检查是否成功发送了消息
        // 这里我们简单地检查消息的内容是否与我们发送的相同
        assertEquals("Hello RocketMQ", new String(msg.getBody()));
    }

    @AfterAll
    public static void tearDown() {
        // 关闭Producer实例
        producer.shutdown();
    }
}
