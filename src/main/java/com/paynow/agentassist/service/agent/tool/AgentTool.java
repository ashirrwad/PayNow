package com.paynow.agentassist.service.agent.tool;

import java.util.concurrent.CompletableFuture;

public interface AgentTool<T, R> {
  String getName();

  String getDescription();

  CompletableFuture<R> execute(T input);

  Class<T> getInputType();

  Class<R> getResultType();
}
