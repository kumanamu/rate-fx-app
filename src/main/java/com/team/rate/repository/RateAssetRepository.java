
package com.team.rate.repository;

import com.team.rate.model.RateAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RateAssetRepository extends JpaRepository<RateAsset, Long> {
    Optional<RateAsset> findBySymbol(String symbol);
}
