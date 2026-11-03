package com.campushub.events;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes pending outbox rows to Kafka (FR-EVT-01). Delivery is at least once: a crash after the broker
 * acknowledges but before the row is stamped publishes it again, and consumers deduplicate. The message key is
 * the aggregate ID, so events for one project or submission stay in order.
 */
@Component
@ConditionalOnProperty(name = "campushub.kafka.enabled", havingValue = "true")
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final long sendTimeoutMillis;

    public OutboxRelay(OutboxEventRepository outbox, KafkaTemplate<String, String> kafka,
                       TransactionTemplate transactions, Clock clock, MeterRegistry metrics,
                       @Value("${campushub.kafka.send-timeout-ms:10000}") long sendTimeoutMillis) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.transactions = transactions;
        this.clock = clock;
        this.metrics = metrics;
        this.sendTimeoutMillis = sendTimeoutMillis;
    }

    @Scheduled(fixedDelayString = "${campushub.kafka.relay-interval-ms:2000}")
    public void relayScheduled() {
        try {
            publishPending();
        } catch (RuntimeException ex) {
            log.warn("Outbox relay pass failed; it will retry on the next tick", ex);
        }
    }

    /**
     * Sends one batch and stamps each acknowledged row. The batch runs in an explicit transaction: a
     * self-invoked {@code @Transactional} method would skip the proxy, so the row locks and the published_at
     * updates would never commit. Stops at the first failure so a broker outage does not reorder events; the
     * failed row keeps its place and its attempt count grows.
     */
    public int publishPending() {
        Integer published = transactions.execute(status -> publishBatch());
        return published == null ? 0 : published;
    }

    private int publishBatch() {
        List<OutboxEvent> batch = outbox.lockNextBatch(BATCH_SIZE);
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                kafka.send(EventTopics.EVENTS, event.getAggregateId(), event.getPayload())
                        .get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
                event.markPublished(clock.instant());
                metrics.counter("campushub.outbox.published", "type", event.getEventType()).increment();
                published++;
            } catch (Exception ex) {
                event.recordFailedAttempt();
                metrics.counter("campushub.outbox.publish.failures", "type", event.getEventType()).increment();
                log.warn("Outbox event {} not published (attempt {}); will retry", event.getId(),
                        event.getPublishAttempts(), ex);
                break;
            }
        }
        return published;
    }
}
