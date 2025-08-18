package com.paynow.agentassist.util;

public class PiiMaskingUtil {

  private PiiMaskingUtil() {
    // Utility class
  }

  public static String maskCustomerId(String customerId) {
    if (customerId == null || customerId.length() <= 6) {
      return customerId;
    }
    return customerId.substring(0, 3) + "***" + customerId.substring(customerId.length() - 2);
  }
}
