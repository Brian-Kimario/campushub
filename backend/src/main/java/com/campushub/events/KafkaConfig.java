package com.campushub.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka wiring, active only when campushub.kafka.enabled is true (ADR-011). Transient failures retry with
 * exponential backoff (FR-EVT-04). Messages that still fail, or that cannot be parsed, go to the dead-letter topic
 * and are counted.
 */
@Configuration
@ConditionalOnProperty(name = "campushub.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Bean
    NewTopic eventsTopic() {
        return new NewTopic(EventTopics.EVENTS, 3, (short) 1);
    }

    @Bean
    NewTopic deadLetterTopic() {
        return new NewTopic(EventTopics.DEAD_LETTER, 1, (short) 1);
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka, MeterRegistry metrics,
                                          @Value("${campushub.kafka.retry.initial-interval-ms:500}") long initialMs,
                                          @Value("${campushub.kafka.retry.max-elapsed-ms:5000}") long maxElapsedMs) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, ex) -> {
                    metrics.counter("campushub.events.dead_lettered").increment();
                    return new TopicPartition(EventTopics.DEAD_LETTER, -1);
                });
        ExponentialBackOff backOff = new ExponentialBackOff(initialMs, 2.0);
        backOff.setMaxElapsedTime(maxElapsedMs);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        return handler;
    }
}
