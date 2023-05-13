package com.github.makewheels.video2022.rocketmq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class TestController {
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @GetMapping("/sendmsg")
    public String sendMessage() {
        rocketMQTemplate.convertAndSend(
                "test-topic", "Hello, RocketMQ!".getBytes());
        return "Message sent";
    }

    public static void main(String[] args) throws MQClientException, MQBrokerException, RemotingException, InterruptedException {
        // 发送消息
        DefaultMQProducer producer2 = new DefaultMQProducer("test-group");
        producer2.setSendMsgTimeout(10000);
        producer2.setNamesrvAddr("staging-cnbj2-rocketmq.namesrv.api.xiaomi.net:9876");
        producer2.start();
        Message message = new Message("m-test-topic", "test-tag", "Hello, RocketMQ!".getBytes());
        SendResult sendResult = producer2.send(message);
        System.out.println(sendResult);

        // 消费消息
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("test-group");
        consumer.setNamesrvAddr("staging-cnbj2-rocketmq.namesrv.api.xiaomi.net:9876");
        consumer.subscribe("m-test-topic", "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt messageExt : msgs) {
                System.out.println(new String(messageExt.getBody()));
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
    }
}