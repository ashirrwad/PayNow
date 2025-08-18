package com.paynow.agentassist.factory;

import com.paynow.agentassist.service.agent.tool.AgentTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("Agent Tool Factory Tests")
class AgentToolFactoryTest {

  @Mock private AgentTool<String, BigDecimal> mockBalanceTool;

  @Mock private AgentTool<String, String> mockRiskTool;

  private AgentToolFactory toolFactory;

  @BeforeEach
  void setUp() {
    lenient().when(mockBalanceTool.getName()).thenReturn("getBalance");
    lenient().when(mockBalanceTool.getDescription()).thenReturn("Gets customer balance");
    lenient().when(mockBalanceTool.getInputType()).thenReturn(String.class);
    lenient().when(mockBalanceTool.getResultType()).thenReturn(BigDecimal.class);

    lenient().when(mockRiskTool.getName()).thenReturn("getRisk");
    lenient().when(mockRiskTool.getDescription()).thenReturn("Gets risk assessment");
    lenient().when(mockRiskTool.getInputType()).thenReturn(String.class);
    lenient().when(mockRiskTool.getResultType()).thenReturn(String.class);

    List<AgentTool<?, ?>> tools = List.of(mockBalanceTool, mockRiskTool);
    toolFactory = new AgentToolFactory(tools);
  }

  @Test
  @DisplayName("Should retrieve tool by name and types")
  void shouldRetrieveToolByNameAndTypes() {
    // When
    AgentTool<String, BigDecimal> tool =
        toolFactory.getTool("getBalance", String.class, BigDecimal.class);

    // Then
    assertNotNull(tool);
    assertEquals("getBalance", tool.getName());
    assertEquals(String.class, tool.getInputType());
    assertEquals(BigDecimal.class, tool.getResultType());
  }

  @Test
  @DisplayName("Should retrieve tool by name only")
  void shouldRetrieveToolByNameOnly() {
    // When
    AgentTool<?, ?> tool = toolFactory.getTool("getRisk");

    // Then
    assertNotNull(tool);
    assertEquals("getRisk", tool.getName());
  }

  @Test
  @DisplayName("Should throw exception for unknown tool")
  void shouldThrowExceptionForUnknownTool() {
    // When & Then
    assertThrows(IllegalArgumentException.class, () -> toolFactory.getTool("unknownTool"));
  }

  @Test
  @DisplayName("Should throw exception for type mismatch")
  void shouldThrowExceptionForTypeMismatch() {
    // When & Then
    assertThrows(
        IllegalArgumentException.class,
        () -> toolFactory.getTool("getBalance", String.class, String.class)); // Wrong result type
  }

  @Test
  @DisplayName("Should return available tools map")
  void shouldReturnAvailableToolsMap() {
    // When
    var availableTools = toolFactory.getAvailableTools();

    // Then
    assertEquals(2, availableTools.size());
    assertTrue(availableTools.containsKey("getBalance"));
    assertTrue(availableTools.containsKey("getRisk"));
    assertEquals("Gets customer balance", availableTools.get("getBalance"));
    assertEquals("Gets risk assessment", availableTools.get("getRisk"));
  }

  @Test
  @DisplayName("Should check tool availability")
  void shouldCheckToolAvailability() {
    // When & Then
    assertTrue(toolFactory.isToolAvailable("getBalance"));
    assertTrue(toolFactory.isToolAvailable("getRisk"));
    assertFalse(toolFactory.isToolAvailable("unknownTool"));
  }

  @Test
  @DisplayName("Should register new tool")
  void shouldRegisterNewTool() {
    // Given
    AgentTool<String, String> newTool = new TestTool("newTool", "New test tool");

    // When
    toolFactory.registerTool(newTool);

    // Then
    assertTrue(toolFactory.isToolAvailable("newTool"));
    assertEquals(newTool, toolFactory.getTool("newTool"));
  }

  @Test
  @DisplayName("Should throw exception when registering duplicate tool")
  void shouldThrowExceptionWhenRegisteringDuplicateTool() {
    // Given
    AgentTool<String, String> duplicateTool = new TestTool("getBalance", "Duplicate tool");

    // When & Then
    assertThrows(IllegalArgumentException.class, () -> toolFactory.registerTool(duplicateTool));
  }

  @Test
  @DisplayName("Should unregister tool")
  void shouldUnregisterTool() {
    // When
    toolFactory.unregisterTool("getRisk");

    // Then
    assertFalse(toolFactory.isToolAvailable("getRisk"));
    assertThrows(IllegalArgumentException.class, () -> toolFactory.getTool("getRisk"));
  }

  // Test implementation of AgentTool for testing
  private static class TestTool implements AgentTool<String, String> {
    private final String name;
    private final String description;

    public TestTool(String name, String description) {
      this.name = name;
      this.description = description;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public CompletableFuture<String> execute(String input) {
      return CompletableFuture.completedFuture("test-result");
    }

    @Override
    public Class<String> getInputType() {
      return String.class;
    }

    @Override
    public Class<String> getResultType() {
      return String.class;
    }
  }
}
