package com.team.rate.controller;

import com.team.rate.api.RateTwelveDataClient;
import com.team.rate.api.RateTwelveDataClient.QuoteData;
import com.team.rate.entity.Asset;
import com.team.rate.entity.DailySummary;
import com.team.rate.repository.AssetRepository;
import com.team.rate.repository.DailySummaryRepository;
import com.team.rate.service.RateFxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RateFxController {

    private final DailySummaryRepository dailyRepo;
    private final AssetRepository assetRepo;
    private final RateFxService fxService;          // fmt2 / fmt2Pct / lastClose* 헬퍼에 사용
    private final RateTwelveDataClient twelve;      // quote 보정용

    // 자산 ID / 심볼
    @Value("${app.fx.asset.usd:1}") private Long usdAssetId;
    @Value("${app.fx.asset.jpy:2}") private Long jpyAssetId;
    @Value("${app.fx.asset.eur:3}") private Long eurAssetId;

    @Value("${app.fx.symbol.usd:USD/KRW}") private String usdSymbol;
    @Value("${app.fx.symbol.jpy:JPY/KRW}") private String jpySymbol;
    @Value("${app.fx.symbol.eur:EUR/KRW}") private String eurSymbol;

    @Value("${twelvedata.apikey}") private String twelveApiKey;

    /**
     * 일별 환율 화면
     * - USD/KRW, JPY/KRW(100엔 환산), EUR/KRW
     * - DB(daily_summary)에 없으면 TwelveData quote로 즉시 보정하여 1행이라도 출력
     */
    @GetMapping("/rate/fx/daily")
    public String dailyPage(Model model) {
        model.addAttribute("usdRows", rowsForAsset(usdAssetId, usdSymbol, false));
        model.addAttribute("jpyRows", rowsForAsset(jpyAssetId, jpySymbol, true));   // 100엔 기준 표시
        model.addAttribute("eurRows", rowsForAsset(eurAssetId, eurSymbol, false));
        return "rate/rate_fx_daily";
    }

    /**
     * 한 종목의 화면용 Row 리스트 생성.
     * 1) daily_summary 최신 60행 사용
     * 2) 없거나 일부 값이 비면 → quote로 보정
     * 3) JPY는 100엔 기준 환산(가격/등락 금액만 ×100, 등락률은 비율이므로 그대로)
     */
    private List<Row> rowsForAsset(Long assetId, String symbol, boolean jpyHundred) {
        Asset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown asset_id: " + assetId));

        List<DailySummary> list = dailyRepo.findTop60ByAssetOrderById_TimestampDesc(asset);
        List<Row> out = new ArrayList<>();

        if (list != null && !list.isEmpty()) {
            for (DailySummary ds : list) {
                String dateText = Objects.requireNonNull(ds.getId()).getTimestamp().toString();

                BigDecimal price = ds.getPrice();
                BigDecimal chg   = ds.getChange();
                BigDecimal pct   = ds.getChangePercent();

                // 값 비면 서비스 헬퍼/quote로 보정 시도
                if (price == null) {
                    price = fxService.lastCloseForDay(asset, ds.getId().getTimestamp())
                            .orElseGet(() -> fxService.lastCloseOverall(asset).orElse(null));
                }
                if ((chg == null || pct == null) && price != null) {
                    var prevOpt = fxService.lastCloseBefore(asset, ds.getId().getTimestamp());
                    if (prevOpt.isPresent() && prevOpt.get().signum() != 0) {
                        BigDecimal prev = prevOpt.get();
                        BigDecimal delta = price.subtract(prev);
                        BigDecimal rate  = delta.multiply(BigDecimal.valueOf(100))
                                .divide(prev, 4, java.math.RoundingMode.HALF_UP);
                        if (chg == null) chg = delta;
                        if (pct == null) pct = rate;
                    }
                }

                // 최후: quote로 한 번 더 보정
                if (price == null) {
                    try {
                        QuoteData q = twelve.fetchQuoteNumbers(symbol, twelveApiKey);
                        price = q.getPrice();
                        if (chg == null) chg = q.getChange();
                        if (pct == null) pct = q.getPercentChange();
                        if ((chg == null || pct == null) && q.getPreviousClose() != null && q.getPreviousClose().signum() != 0) {
                            BigDecimal delta = price.subtract(q.getPreviousClose());
                            BigDecimal rate  = delta.multiply(BigDecimal.valueOf(100))
                                    .divide(q.getPreviousClose(), 4, java.math.RoundingMode.HALF_UP);
                            if (chg == null) chg = delta;
                            if (pct == null) pct = rate;
                        }
                    } catch (Exception ex) {
                        log.warn("Quote fallback failed for {}: {}", symbol, ex.getMessage());
                    }
                }

                // 100엔 기준
                if (jpyHundred && price != null) {
                    price = price.multiply(BigDecimal.valueOf(100));
                    if (chg != null) chg = chg.multiply(BigDecimal.valueOf(100));
                }

                boolean up   = chg != null && chg.signum() > 0;
                boolean down = chg != null && chg.signum() < 0;

                out.add(new Row(
                        dateText,
                        fxService.fmt2(price),
                        fxService.fmt2(chg),
                        fxService.fmt2Pct(pct),
                        up, down
                ));
            }
            return out;
        }

        // daily_summary 자체가 비어 있으면: quote로 1행이라도 표시
        try {
            QuoteData q = twelve.fetchQuoteNumbers(symbol, twelveApiKey);
            BigDecimal price = q.getPrice();
            BigDecimal chg   = q.getChange();
            BigDecimal pct   = q.getPercentChange();

            if (jpyHundred && price != null) {
                price = price.multiply(BigDecimal.valueOf(100));
                if (chg != null) chg = chg.multiply(BigDecimal.valueOf(100));
            }

            boolean up   = chg != null && chg.signum() > 0;
            boolean down = chg != null && chg.signum() < 0;

            out.add(new Row(
                    java.time.LocalDate.now().toString(),
                    fxService.fmt2(price),
                    fxService.fmt2(chg),
                    fxService.fmt2Pct(pct),
                    up, down
            ));
        } catch (Exception ex) {
            log.warn("Quote only fallback failed for {}: {}", symbol, ex.getMessage());
        }
        return out;
    }

    /** 뷰 모델: html이 기대하는 문자열 필드/플래그 그대로 제공 */
    public static class Row {
        private final String date;
        private final String priceText;
        private final String changeText;
        private final String changePercentText;
        private final boolean up;
        private final boolean down;

        public Row(String date, String priceText, String changeText, String changePercentText, boolean up, boolean down) {
            this.date = date;
            this.priceText = priceText;
            this.changeText = changeText;
            this.changePercentText = changePercentText;
            this.up = up;
            this.down = down;
        }

        public String getDate() { return date; }
        public String getPriceText() { return priceText; }
        public String getChangeText() { return changeText; }
        public String getChangePercentText() { return changePercentText; }
        public boolean isUp() { return up; }
        public boolean isDown() { return down; }
    }
}
