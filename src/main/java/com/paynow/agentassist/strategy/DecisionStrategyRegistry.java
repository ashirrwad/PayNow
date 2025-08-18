package com.paynow.agentassist.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DecisionStrategyRegistry {

  private final Map<String, DecisionStrategy> strategies;
  private final DecisionStrategy defaultStrategy;

  public DecisionStrategyRegistry(List<DecisionStrategy> strategyList) {
    this.strategies =
        new ConcurrentHashMap<>(
            strategyList.stream()
                .collect(Collectors.toMap(DecisionStrategy::getName, Function.identity())));

    this.defaultStrategy = strategies.get("default");
    if (this.defaultStrategy == null) {
      throw new IllegalStateException("Default decision strategy not found");
    }
  }

  public DecisionStrategy getStrategy(String strategyName) {
    return strategies.getOrDefault(strategyName, defaultStrategy);
  }

  public DecisionStrategy getDefaultStrategy() {
    return defaultStrategy;
  }

  public Map<String, String> getAvailableStrategies() {
    return strategies.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getDescription()));
  }

  public boolean isValidStrategy(String strategyName) {
    return strategies.containsKey(strategyName);
  }
}
