package com.paynow.agentassist.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class EventPublisher {

  private static final Logger logger = LoggerFactory.getLogger(EventPublisher.class);
  private static final Logger eventLogger = LoggerFactory.getLogger("PAYMENT_EVENTS");

  private final ObjectMapper objectMapper;
  private final BlockingQueue<PaymentDecisionEvent> eventQueue;

  public EventPublisher(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.eventQueue = new LinkedBlockingQueue<>();
    startEventProcessor();
  }

  public void publishPaymentDecision(PaymentDecisionEvent event) {
    try {
      eventQueue.offer(event);
      logger.debug("Payment decision event queued: {}", event.eventId());
    } catch (Exception e) {
      logger.error("Failed to queue payment decision event: {}", event.eventId(), e);
    }
  }

  private void startEventProcessor() {
    Thread eventProcessor =
        new Thread(
            () -> {
              while (!Thread.currentThread().isInterrupted()) {
                try {
                  PaymentDecisionEvent event = eventQueue.take();
                  processEvent(event);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  logger.info("Event processor interrupted");
                  break;
                } catch (Exception e) {
                  logger.error("Error processing event", e);
                }
              }
            });

    eventProcessor.setName("payment-event-processor");
    eventProcessor.setDaemon(true);
    eventProcessor.start();

    logger.info("Payment event processor started");
  }

  private void processEvent(PaymentDecisionEvent event) {
    try {
      // Simulate Kafka publishing by logging to stdout and dedicated logger
      String eventJson = objectMapper.writeValueAsString(event);

      // Log to stdout (simulating Kafka producer)
      System.out.println("KAFKA_EVENT: " + eventJson);

      // Log to dedicated event logger (for auditing)
      eventLogger.info(
          "PUBLISHED: topic=payment.decided, partition=0, offset={}, event={}",
          System.currentTimeMillis(),
          eventJson);

      logger.debug("Successfully published payment decision event: {}", event.eventId());

    } catch (Exception e) {
      logger.error("Failed to publish payment decision event: {}", event.eventId(), e);
    }
  }

  public int getQueueSize() {
    return eventQueue.size();
  }
}
