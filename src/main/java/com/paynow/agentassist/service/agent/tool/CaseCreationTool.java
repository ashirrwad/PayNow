package com.paynow.agentassist.service.agent.tool;

import com.paynow.agentassist.dto.CaseCreationRequest;
import com.paynow.agentassist.dto.CaseCreationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CaseCreationTool implements AgentTool<CaseCreationRequest, CaseCreationResult> {

  private static final Logger logger = LoggerFactory.getLogger(CaseCreationTool.class);

  @Override
  public String getName() {
    return "createCase";
  }

  @Override
  public String getDescription() {
    return "Creates a manual review case for blocked or review decisions";
  }

  @Override
  public CompletableFuture<CaseCreationResult> execute(CaseCreationRequest request) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            logger.debug(
                "Creating case for customer: {}, reason: {}",
                request.customerId(),
                request.reason());
            Thread.sleep(100); // Simulate case management system call

            String caseId =
                "case_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String assignedTo =
                "HIGH".equals(request.priority()) ? "senior_analyst" : "analyst_team";

            logger.info(
                "Created case {} for customer {} with priority {}",
                caseId,
                request.customerId(),
                request.priority());

            return new CaseCreationResult(caseId, "CREATED", assignedTo);

          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Case creation interrupted", e);
          } catch (Exception e) {
            logger.error("Failed to create case for customer: {}", request.customerId(), e);
            throw new RuntimeException("Case creation failed", e);
          }
        });
  }

  @Override
  public Class<CaseCreationRequest> getInputType() {
    return CaseCreationRequest.class;
  }

  @Override
  public Class<CaseCreationResult> getResultType() {
    return CaseCreationResult.class;
  }
}
