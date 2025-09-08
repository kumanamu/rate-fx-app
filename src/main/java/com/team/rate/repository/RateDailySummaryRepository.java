
package com.team.rate.repository;

import com.team.rate.model.RateDailySummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RateDailySummaryRepository extends JpaRepository<RateDailySummary, RateDailySummary.PK> {

    List<RateDailySummary> findTop15ByAssetIdOrderByTimestampDesc(Long assetId);

    List<RateDailySummary> findByAssetIdAndTimestampBetweenOrderByTimestampDesc(
            Long assetId, LocalDate from, LocalDate to);
}
