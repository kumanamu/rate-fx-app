
package com.team.rate.config;

import com.team.rate.model.RateAsset;
import com.team.rate.repository.RateAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RateBootstrap {

    private final RateAssetRepository assetRepository;

    @Bean
    public CommandLineRunner initRateAssets() {
        return args -> {
            createIfAbsent("USD/KRW", "원/달러", "USDKRW");
            createIfAbsent("JPY/KRW", "원/엔", "JPYKRW");
            createIfAbsent("EUR/KRW", "원/유로", "EURKRW");
        };
    }

    private void createIfAbsent(String symbol, String name, String alt) {
        assetRepository.findBySymbol(symbol).orElseGet(() ->
                assetRepository.save(RateAsset.builder()
                        .symbol(symbol).name(name).symbolAlt(alt).build())
        );
    }
}
