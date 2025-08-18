package com.paynow.agentassist.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class ResourceManager {

  private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);

  // Thread-safe collections for resource management
  private final ConcurrentHashMap<String, ExecutorService> executorServices;
  private final ConcurrentHashMap<String, ReentrantLock> resourceLocks;
  private final ConcurrentHashMap<String, AutoCloseable> managedResources;

  // Shared thread pools
  private final ExecutorService agentToolExecutor;
  private final ExecutorService eventProcessingExecutor;

  public ResourceManager() {
    this.executorServices = new ConcurrentHashMap<>();
    this.resourceLocks = new ConcurrentHashMap<>();
    this.managedResources = new ConcurrentHashMap<>();

    // Initialize shared thread pools with proper naming and sizing
    this.agentToolExecutor =
        Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2,
            r -> {
              Thread t = new Thread(r, "agent-tool-" + System.currentTimeMillis());
              t.setDaemon(true);
              return t;
            });

    this.eventProcessingExecutor =
        Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            r -> {
              Thread t = new Thread(r, "event-processor-" + System.currentTimeMillis());
              t.setDaemon(true);
              return t;
            });

    executorServices.put("agentToolExecutor", agentToolExecutor);
    executorServices.put("eventProcessingExecutor", eventProcessingExecutor);

    logger.info(
        "ResourceManager initialized with {} CPU cores",
        Runtime.getRuntime().availableProcessors());
  }

  public ExecutorService getAgentToolExecutor() {
    return agentToolExecutor;
  }

  public ExecutorService getEventProcessingExecutor() {
    return eventProcessingExecutor;
  }

  public ExecutorService getOrCreateExecutor(String name, int poolSize) {
    return executorServices.computeIfAbsent(
        name,
        k -> {
          ExecutorService executor =
              Executors.newFixedThreadPool(
                  poolSize,
                  r -> {
                    Thread t = new Thread(r, name + "-" + System.currentTimeMillis());
                    t.setDaemon(true);
                    return t;
                  });
          logger.info("Created executor service: {} with pool size: {}", name, poolSize);
          return executor;
        });
  }

  public ReentrantLock getResourceLock(String resourceId) {
    return resourceLocks.computeIfAbsent(
        resourceId,
        k -> {
          logger.debug("Created resource lock for: {}", resourceId);
          return new ReentrantLock(true); // Fair lock
        });
  }

  public void registerManagedResource(String resourceId, AutoCloseable resource) {
    AutoCloseable existing = managedResources.put(resourceId, resource);
    if (existing != null) {
      logger.warn("Replaced existing managed resource: {}", resourceId);
      closeResourceSafely(resourceId, existing);
    } else {
      logger.debug("Registered managed resource: {}", resourceId);
    }
  }

  public void unregisterManagedResource(String resourceId) {
    AutoCloseable resource = managedResources.remove(resourceId);
    if (resource != null) {
      closeResourceSafely(resourceId, resource);
      logger.debug("Unregistered managed resource: {}", resourceId);
    }
  }

  public <T> T executeWithLock(String resourceId, java.util.function.Supplier<T> operation) {
    ReentrantLock lock = getResourceLock(resourceId);
    lock.lock();
    try {
      return operation.get();
    } finally {
      lock.unlock();
    }
  }

  public void executeWithLock(String resourceId, Runnable operation) {
    ReentrantLock lock = getResourceLock(resourceId);
    lock.lock();
    try {
      operation.run();
    } finally {
      lock.unlock();
    }
  }

  @PreDestroy
  public void cleanup() {
    logger.info("Starting ResourceManager cleanup...");

    // Close all managed resources
    managedResources.forEach(this::closeResourceSafely);
    managedResources.clear();

    // Shutdown all executor services
    executorServices.forEach(this::shutdownExecutorSafely);
    executorServices.clear();

    // Clear locks
    resourceLocks.clear();

    logger.info("ResourceManager cleanup completed");
  }

  private void closeResourceSafely(String resourceId, AutoCloseable resource) {
    try {
      resource.close();
      logger.debug("Successfully closed resource: {}", resourceId);
    } catch (Exception e) {
      logger.error("Failed to close resource: {}", resourceId, e);
    }
  }

  private void shutdownExecutorSafely(String name, ExecutorService executor) {
    try {
      logger.info("Shutting down executor: {}", name);
      executor.shutdown();

      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        logger.warn("Executor {} did not terminate gracefully, forcing shutdown", name);
        executor.shutdownNow();

        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
          logger.error("Executor {} failed to terminate", name);
        }
      } else {
        logger.info("Executor {} shut down successfully", name);
      }
    } catch (InterruptedException e) {
      logger.error("Interrupted while shutting down executor: {}", name);
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
