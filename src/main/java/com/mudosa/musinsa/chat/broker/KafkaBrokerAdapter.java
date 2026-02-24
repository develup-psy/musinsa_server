package com.mudosa.musinsa.chat.broker;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaBrokerAdapter implements ChatMessageBroker {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public KafkaBrokerAdapter(KafkaTemplate<String, Object> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @Override
  public void sendToTopic(String destination, Object payload) {
    kafkaTemplate.send(destination, payload);
  }

  @Override
  public void sendToUser(String sessionId, String destination, Object payload) {
    kafkaTemplate.send(destination + "-" + sessionId, payload);
  }

  @Override
  public void broadcast(String destination, Object payload) {
    kafkaTemplate.send(destination, payload);
  }
}
