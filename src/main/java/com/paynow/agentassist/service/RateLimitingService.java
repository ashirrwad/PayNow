package com.paynow.agentassist.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RateLimitingService {

  private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  @Value("${paynow.rate-limit.requests-per-second:5}")
  private int requestsPerSecond;

  @Value("${paynow.rate-limit.bucket-capacity:10}")
  private int bucketCapacity;

  public boolean isAllowed(String customerId) {
    Bucket bucket = buckets.computeIfAbsent(customerId, this::createBucket);
    return bucket.tryConsume(1);
  }

  public long getAvailableTokens(String customerId) {
    Bucket bucket = buckets.get(customerId);
    return bucket != null ? bucket.getAvailableTokens() : bucketCapacity;
  }

  private Bucket createBucket(String customerId) {
    Bandwidth limit =
        Bandwidth.classic(
            bucketCapacity, Refill.intervally(requestsPerSecond, Duration.ofSeconds(1)));
    return Bucket.builder().addLimit(limit).build();
  }
}
