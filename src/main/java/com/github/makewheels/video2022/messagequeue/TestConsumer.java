package com.github.makewheels.video2022.messagequeue;

import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(topic = "test-topic", consumerGroup = "test-group",
        consumeMode = ConsumeMode.CONCURRENTLY)
public class TestConsumer implements RocketMQListener<String> {
    @Override
    public void onMessage(String message) {
        System.out.println("Received message: " + message);
    }
}