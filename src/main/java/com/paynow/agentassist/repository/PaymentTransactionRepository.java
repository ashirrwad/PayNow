package com.paynow.agentassist.repository;

import com.paynow.agentassist.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

  Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

  Optional<PaymentTransaction> findByRequestId(String requestId);

  @Query("SELECT COUNT(pt) FROM PaymentTransaction pt WHERE pt.customerId = :customerId")
  long countByCustomerId(@Param("customerId") String customerId);

  boolean existsByIdempotencyKey(String idempotencyKey);
}
