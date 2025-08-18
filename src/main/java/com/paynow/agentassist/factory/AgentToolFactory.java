package com.paynow.agentassist.factory;

import com.paynow.agentassist.service.agent.tool.AgentTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AgentToolFactory {

  private final Map<String, AgentTool<?, ?>> tools;

  public AgentToolFactory(List<AgentTool<?, ?>> toolList) {
    this.tools =
        new ConcurrentHashMap<>(
            toolList.stream().collect(Collectors.toMap(AgentTool::getName, Function.identity())));
  }

  @SuppressWarnings("unchecked")
  public <T, R> AgentTool<T, R> getTool(String toolName, Class<T> inputType, Class<R> resultType) {
    AgentTool<?, ?> tool = tools.get(toolName);
    if (tool == null) {
      throw new IllegalArgumentException("Tool not found: " + toolName);
    }

    if (!tool.getInputType().equals(inputType) || !tool.getResultType().equals(resultType)) {
      throw new IllegalArgumentException("Tool type mismatch for: " + toolName);
    }

    return (AgentTool<T, R>) tool;
  }

  public AgentTool<?, ?> getTool(String toolName) {
    AgentTool<?, ?> tool = tools.get(toolName);
    if (tool == null) {
      throw new IllegalArgumentException("Tool not found: " + toolName);
    }
    return tool;
  }

  public Map<String, String> getAvailableTools() {
    return tools.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getDescription()));
  }

  public boolean isToolAvailable(String toolName) {
    return tools.containsKey(toolName);
  }

  public void registerTool(AgentTool<?, ?> tool) {
    if (tools.containsKey(tool.getName())) {
      throw new IllegalArgumentException("Tool already registered: " + tool.getName());
    }
    tools.put(tool.getName(), tool);
  }

  public void unregisterTool(String toolName) {
    tools.remove(toolName);
  }
}
