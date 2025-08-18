package com.paynow.agentassist.repository;

import com.paynow.agentassist.entity.CustomerBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface CustomerBalanceRepository extends JpaRepository<CustomerBalance, Long> {

  Optional<CustomerBalance> findByCustomerId(String customerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT cb FROM CustomerBalance cb WHERE cb.customerId = :customerId")
  Optional<CustomerBalance> findByCustomerIdWithLock(@Param("customerId") String customerId);

  boolean existsByCustomerId(String customerId);
}
