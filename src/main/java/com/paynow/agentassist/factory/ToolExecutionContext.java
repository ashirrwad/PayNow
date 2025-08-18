package com.paynow.agentassist.factory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToolExecutionContext {

  private final String requestId;
  private final String customerId;
  private final LocalDateTime startTime;
  private final Map<String, Object> properties;

  private ToolExecutionContext(String requestId, String customerId) {
    this.requestId = requestId;
    this.customerId = customerId;
    this.startTime = LocalDateTime.now();
    this.properties = new ConcurrentHashMap<>();
  }

  public static ToolExecutionContext create(String requestId, String customerId) {
    return new ToolExecutionContext(requestId, customerId);
  }

  public String getRequestId() {
    return requestId;
  }

  public String getCustomerId() {
    return customerId;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public void setProperty(String key, Object value) {
    properties.put(key, value);
  }

  @SuppressWarnings("unchecked")
  public <T> T getProperty(String key, Class<T> type) {
    Object value = properties.get(key);
    if (value == null) {
      return null;
    }
    if (!type.isInstance(value)) {
      throw new ClassCastException("Property " + key + " is not of type " + type.getName());
    }
    return (T) value;
  }

  public Map<String, Object> getAllProperties() {
    return Map.copyOf(properties);
  }
}
