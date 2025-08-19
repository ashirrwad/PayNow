package com.paynow.agentassist.repository;

import com.paynow.agentassist.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {

  Optional<ApiKeyEntity> findByKeyHash(String keyHash);

  Optional<ApiKeyEntity> findByKeyHashAndActiveTrue(String keyHash);

  List<ApiKeyEntity> findByUserIdAndActiveTrue(String userId);

  List<ApiKeyEntity> findByUserId(String userId);

  @Modifying
  @Query(
      "UPDATE ApiKeyEntity a SET a.lastUsedAt = :lastUsedAt, a.usageCount = a.usageCount + 1 WHERE a.id = :id")
  void updateUsageStats(@Param("id") Long id, @Param("lastUsedAt") LocalDateTime lastUsedAt);
}
