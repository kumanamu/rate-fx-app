package com.team.rate.service;

import com.team.rate.api.RateTwelveDataClient;
import com.team.rate.api.RateTwelveDataClient.TwelveBar;
import com.team.rate.entity.Asset;
import com.team.rate.entity.DailySummary;
import com.team.rate.entity.DailySummaryId;
import com.team.rate.entity.Price;
import com.team.rate.repository.AssetRepository;
import com.team.rate.repository.DailySummaryRepository;
import com.team.rate.repository.PriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * TwelveData → prices / daily_summary 업서트 + 화면 보정 헬퍼
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateFxService {

    // ====== 주입 ======
    private final RateTwelveDataClient twelve;
    private final AssetRepository assetRepo;
    private final PriceRepository priceRepo;
    private final DailySummaryRepository dailyRepo;

    // ====== 설정값 ======
    @Value("${twelvedata.apikey}") private String apiKey;

    @Value("${app.fx.asset.usd:1}") private Long usdAssetId;
    @Value("${app.fx.asset.jpy:2}") private Long jpyAssetId;
    @Value("${app.fx.asset.eur:3}") private Long eurAssetId;

    @Value("${app.fx.symbol.usd:USD/KRW}") private String usdSymbol;
    @Value("${app.fx.symbol.jpy:JPY/KRW}") private String jpySymbol;
    @Value("${app.fx.symbol.eur:EUR/KRW}") private String eurSymbol;

    // ====== 인입(수집) ======
    @Transactional
    public void refreshFx(int days) {
        ingestOne(usdAssetId, usdSymbol, days);
        ingestOne(jpyAssetId, jpySymbol, days);
        ingestOne(eurAssetId, eurSymbol, days);
    }

    /** 한 자산(symbol)에 대해 일봉 수집 → prices/daily_summary 업서트 */
    private void ingestOne(Long assetId, String symbol, int days) {
        // 1) 자산 로드 (id 우선, 없으면 symbol로)
        Asset asset = assetRepo.findById(assetId)
                .orElseGet(() -> assetRepo.findBySymbolIgnoreCase(symbol)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Asset not found. id=" + assetId + ", symbol=" + symbol)));

        log.info("[FX Ingest] assetId={}, symbol={}, days={}", asset.getAssetId(), symbol, days);

        // 2) TwelveData 일봉
        List<TwelveBar> bars = twelve.fetchDailySeries(symbol, days, apiKey);
        if (bars == null || bars.isEmpty()) {
            log.warn("[FX Ingest] No data from TwelveData for symbol={}", symbol);
            return;
        }

        // 3) prices 업서트 (원천 시계열 저장)
        for (TwelveBar b : bars) {
            upsertPrice(asset, b.getDatetime(),
                    nz(b.getOpen()), nz(b.getClose()), nz(b.getHigh()), nz(b.getLow()));
        }

        // 4) 날짜 → 종가 맵 (오름차순)
        Map<LocalDate, BigDecimal> dateClose = new TreeMap<>();
        for (TwelveBar b : bars) {
            LocalDate d = b.getDatetime().toLocalDate();
            dateClose.put(d, nz(b.getClose())); // 같은 날짜가 여러 개면 마지막으로 덮음
        }

        // 5) prevClose/등락/등락률 계산 → daily_summary 업서트
        BigDecimal prevClose = null;
        for (Map.Entry<LocalDate, BigDecimal> e : dateClose.entrySet()) {
            LocalDate day   = e.getKey();
            BigDecimal close = e.getValue();

            BigDecimal chg = null, chgPct = null;
            if (prevClose != null && prevClose.compareTo(BigDecimal.ZERO) != 0) {
                chg = close.subtract(prevClose);
                chgPct = chg.multiply(BigDecimal.valueOf(100))
                        .divide(prevClose, 4, RoundingMode.HALF_UP);
            }

            // prevClose까지 저장(첫 날은 null일 수 있으니 0으로 기본값 보정도 가능)
            upsertDaily(asset, day, close, prevClose, chg, chgPct);

            prevClose = close;
        }
    }

    /** prices 테이블 업서트 */
    private void upsertPrice(Asset asset, LocalDateTime ts,
                             BigDecimal open, BigDecimal close, BigDecimal high, BigDecimal low) {
        priceRepo.findByAssetAndTimestamp(asset, ts).ifPresentOrElse(p -> {
            p.setOpen(open);
            p.setClose(close);
            p.setHigh(high);
            p.setLow(low);
            priceRepo.save(p);
        }, () -> {
            Price p = Price.builder()
                    .asset(asset)
                    .timestamp(ts)
                    .open(open)
                    .close(close)
                    .high(high)
                    .low(low)
                    .build();
            priceRepo.save(p);
        });
    }

    /** daily_summary 테이블 업서트 (prev_close 포함) */
    private void upsertDaily(Asset asset, LocalDate day,
                             BigDecimal price, BigDecimal prevClose,
                             BigDecimal change, BigDecimal changePct) {
        DailySummaryId id = new DailySummaryId(asset.getAssetId(), day);
        dailyRepo.findById(id).ifPresentOrElse(ds -> {
            if (price != null)     ds.setPrice(price);
            // prev_close는 DB 제약을 고려해 null이면 0으로도 가능 → 여기선 null 허용이지만 nz 적용해도 안전
            ds.setPrevClose(prevClose); // 또는 ds.setPrevClose(nz(prevClose));
            if (change != null)    ds.setChange(change);
            if (changePct != null) ds.setChangePercent(changePct);
            dailyRepo.save(ds);
        }, () -> {
            DailySummary ds = DailySummary.builder()
                    .id(id)
                    .asset(asset)
                    .price(nz(price))
                    .prevClose(prevClose)               // 또는 nz(prevClose)
                    .change(nz(change))
                    .changePercent(nz(changePct))
                    .build();
            dailyRepo.save(ds);
        });
    }

    private BigDecimal nz(BigDecimal v) {
        return (v == null) ? BigDecimal.ZERO : v;
    }

    // ====== 화면 보정용 헬퍼 (컨트롤러에서 사용) ======
    public Optional<BigDecimal> lastCloseForDay(Asset asset, LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return priceRepo
                .findTop1ByAssetAndTimestampBetweenOrderByTimestampDesc(asset, start, end)
                .map(Price::getClose);
    }

    public Optional<BigDecimal> lastCloseOverall(Asset asset) {
        return priceRepo.findTop1ByAssetOrderByTimestampDesc(asset).map(Price::getClose);
    }

    public Optional<BigDecimal> lastCloseBefore(Asset asset, LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        return priceRepo
                .findTop1ByAssetAndTimestampLessThanOrderByTimestampDesc(asset, start)
                .map(Price::getClose);
    }

    // (선택) 화면 포맷 도우미 — 컨트롤러에서 문자열로 쓰고 있다면 유지
    private static final DecimalFormat DF2 = new DecimalFormat("#,##0.00");
    public String fmt2(BigDecimal v) {
        if (v == null) return "-";
        synchronized (DF2) { return DF2.format(v); }
    }
    public String fmt2Pct(BigDecimal v) {
        if (v == null) return "-";
        synchronized (DF2) { return DF2.format(v) + "%"; }
    }
}
