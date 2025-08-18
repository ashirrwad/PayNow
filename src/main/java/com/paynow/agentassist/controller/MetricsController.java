package com.paynow.agentassist.controller;

import com.paynow.agentassist.constants.ApiConstants;
import com.paynow.agentassist.dto.ApiResponse;
import com.paynow.agentassist.service.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

  private final MetricsService metricsService;

  public MetricsController(MetricsService metricsService) {
    this.metricsService = metricsService;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> getMetrics() {
    Map<String, Object> metrics = metricsService.getSystemMetrics();
    return ApiResponse.success(metrics, ApiConstants.MSG_METRICS_RETRIEVED);
  }
}
