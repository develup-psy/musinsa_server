package com.mudosa.musinsa.payment.config;

import com.mudosa.musinsa.payment.application.dto.queue.PaymentQueueMessage;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class PaymentQueueKafkaConfig {

    private final KafkaProperties kafkaProperties;

    @Bean
    public ConsumerFactory<String, PaymentQueueMessage> paymentQueueConsumerFactory() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mudosa.musinsa.payment.application.dto.queue");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentQueueMessage.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(PaymentQueueMessage.class, false)
        );
    }

    @Bean(name = "paymentQueueKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, PaymentQueueMessage> paymentQueueKafkaListenerContainerFactory(
            ConsumerFactory<String, PaymentQueueMessage> paymentQueueConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentQueueMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentQueueConsumerFactory);
        return factory;
    }
}
