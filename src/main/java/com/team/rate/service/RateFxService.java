
package com.team.rate.service;

import com.team.rate.api.RateTwelveDataClient;
import com.team.rate.dto.RateDailyRowDto;
import com.team.rate.model.RateAsset;
import com.team.rate.model.RateDailySummary;
import com.team.rate.model.RatePrice;
import com.team.rate.repository.RateAssetRepository;
import com.team.rate.repository.RateDailySummaryRepository;
import com.team.rate.repository.RatePriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RateFxService {

    private final RateTwelveDataClient client;
    private final RateAssetRepository assetRepo;
    private final RatePriceRepository priceRepo;
    private final RateDailySummaryRepository summaryRepo;

    public static final List<String> TARGET_SYMBOLS = List.of("USD/KRW","JPY/KRW","EUR/KRW");

    private static final Map<String, Integer> DISPLAY_MULTIPLIER = Map.of(
            "JPY/KRW", 100,
            "USD/KRW", 1,
            "EUR/KRW", 1
    );

    @Transactional
    public void refreshAllFromApi() {
        for (String symbol : TARGET_SYMBOLS) {
            RateAsset asset = assetRepo.findBySymbol(symbol)
                    .orElseThrow(() -> new IllegalStateException("Asset not found: " + symbol));

            var series = client.fetchDailySeries(symbol);

            series.forEach(v -> upsertPrice(asset, v.date(), v.getOpen(), v.getClose(), v.getHigh(), v.getLow()));

            for (int i = 0; i + 1 < series.size(); i++) {
                var today = series.get(i);
                var prev  = series.get(i + 1);
                upsertSummary(asset, today.date(), prev.getClose(), today.getClose());
            }
        }
    }

    private void upsertPrice(RateAsset asset, LocalDate date, BigDecimal open,
                             BigDecimal close, BigDecimal high, BigDecimal low) {
        priceRepo.findByAssetAndTimestamp(asset, date).ifPresentOrElse(existing -> {
            existing.setOpen(open); existing.setClose(close);
            existing.setHigh(high); existing.setLow(low);
        }, () -> priceRepo.save(RatePrice.builder()
                .asset(asset).timestamp(date)
                .open(open).close(close).high(high).low(low).build()));
    }

    private void upsertSummary(RateAsset asset, LocalDate date, BigDecimal prevClose, BigDecimal todayClose) {
        BigDecimal change = todayClose.subtract(prevClose);
        BigDecimal changePct = prevClose.signum() == 0 ? BigDecimal.ZERO :
                change.divide(prevClose, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(4, RoundingMode.HALF_UP);

        RateDailySummary.PK id = new RateDailySummary.PK(asset.getAssetId(), date);
        summaryRepo.findById(id).ifPresentOrElse(s -> {
            s.setPrevClose(prevClose);
            s.setChange(change);
            s.setChangePercent(changePct);
        }, () -> summaryRepo.save(RateDailySummary.builder()
                .assetId(asset.getAssetId())
                .timestamp(date)
                .prevClose(prevClose)
                .change(change)
                .changePercent(changePct)
                .build()));
    }

    @Transactional(readOnly = true)
    public List<RateDailyRowDto> loadRowsFor(String symbol, int days) {
        RateAsset asset = assetRepo.findBySymbol(symbol).orElseThrow();
        var list = summaryRepo.findTop15ByAssetIdOrderByTimestampDesc(asset.getAssetId());

        int mult = DISPLAY_MULTIPLIER.getOrDefault(symbol, 1);
        List<RateDailyRowDto> rows = new ArrayList<>();
        list.stream().limit(days).forEach(s -> {
            BigDecimal price = s.getPrevClose().add(s.getChange())
                    .multiply(BigDecimal.valueOf(mult))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal chg   = s.getChange().multiply(BigDecimal.valueOf(mult))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal pct   = s.getChangePercent();

            rows.add(RateDailyRowDto.builder()
                    .date(s.getTimestamp())
                    .pairName(asset.getName())
                    .price(price)
                    .change(chg)
                    .changePercent(pct)
                    .build());
        });
        return rows;
    }
}
