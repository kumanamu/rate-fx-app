
package com.team.rate.repository;

import com.team.rate.model.RateAsset;
import com.team.rate.model.RatePrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RatePriceRepository extends JpaRepository<RatePrice, Long> {
    Optional<RatePrice> findByAssetAndTimestamp(RateAsset asset, LocalDate date);
    List<RatePrice> findTop15ByAssetOrderByTimestampDesc(RateAsset asset);
}
