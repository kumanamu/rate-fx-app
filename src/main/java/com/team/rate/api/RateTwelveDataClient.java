package com.team.rate.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Twelve Data thin client
 *
 * - time_series(일봉) : /time_series?symbol=USD/KRW&interval=1day&outputsize=60&apikey=...
 * - quote(현재가)     : /quote?symbol=USD/KRW&apikey=...
 * - raw 진단          : fetchDailySeriesRaw(...)
 *
 * ⚠️ 주의: 과거에 내부 record 이름을 "Value"로 쓰면 Spring @Value와 충돌하므로
 *         여기서는 TsEntry로 명명합니다.
 */
@Component
public class RateTwelveDataClient {

    private final RestClient rest;

    // 유연 파서용 포맷들: 날짜만/스페이스 포함/ISO T 포함
    private static final DateTimeFormatter D_DATE       = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter D_DATETIME_1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter D_DATETIME_2 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public RateTwelveDataClient(
            @org.springframework.beans.factory.annotation.Value("${twelvedata.base-url}") String baseUrl // 완전수식으로 충돌 방지
    ) {
        this.rest = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /* ===========================
       Raw 진단용 (원본 JSON 그대로)
       =========================== */
    public String fetchDailySeriesRaw(String symbol, int outputSize, String apiKey) {
        String uri = "/time_series?symbol={symbol}&interval=1day&outputsize={size}&apikey={apikey}";
        return rest.get()
                .uri(uri, symbol, outputSize, apiKey)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
    }

    /* ===========================
       일봉 시계열 (유연 파서 적용)
       =========================== */
    public List<TwelveBar> fetchDailySeries(String symbol, int outputSize, String apiKey) {
        String uri = "/time_series?symbol={symbol}&interval=1day&outputsize={size}&apikey={apikey}";
        TimeSeriesResponse body = rest.get()
                .uri(uri, symbol, outputSize, apiKey)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(TimeSeriesResponse.class);

        if (body == null) {
            throw new IllegalStateException("TwelveData: empty response");
        }
        if ("error".equalsIgnoreCase(body.status)) {
            String code = body.code == null ? "" : body.code;
            String msg  = body.message == null ? "Unknown error" : body.message;
            throw new IllegalStateException("TwelveData error (" + code + "): " + msg);
        }
        if (body.values == null || body.values.isEmpty()) {
            throw new IllegalStateException("TwelveData: no values for symbol=" + symbol);
        }

        List<TwelveBar> out = new ArrayList<>();
        for (TsEntry v : body.values) {
            try {
                out.add(new TwelveBar(
                        parseDateTimeFlexible(v.datetime),
                        parseBig(v.open),
                        parseBig(v.high),
                        parseBig(v.low),
                        parseBig(v.close)
                ));
            } catch (Exception e) {
                // 한 건 파싱 실패해도 전체는 지속
                System.err.println("[TwelveData] skip bad entry: " + v.datetime + " -> " + e.getMessage());
            }
        }
        return out;
    }

    /* ===========================
       quote (현재가/등락/전일가 등)
       =========================== */
    public QuoteData fetchQuoteNumbers(String symbol, String apiKey) {
        String uri = "/quote?symbol={symbol}&apikey={apikey}";
        QuoteResp r = rest.get()
                .uri(uri, symbol, apiKey)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(QuoteResp.class);

        if (r == null) {
            throw new IllegalStateException("TwelveData: empty quote");
        }
        if (r.status != null && !"ok".equalsIgnoreCase(r.status)) {
            String code = r.code == null ? "" : r.code;
            String msg  = r.message == null ? "Unknown error" : r.message;
            throw new IllegalStateException("TwelveData quote error (" + code + "): " + msg);
        }

        return new QuoteData(
                parseBig(r.price),
                parseBig(r.change),
                parseBig(r.percentChange),
                parseBig(r.previousClose),
                parseBig(r.open),
                parseBig(r.high),
                parseBig(r.low)
        );
    }

    /* ===========================
       유틸: 파서/숫자 변환
       =========================== */
    private static LocalDateTime parseDateTimeFlexible(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("empty datetime");
        }
        // 1) "2025-09-09"
        try { return LocalDate.parse(s, D_DATE).atStartOfDay(); } catch (Exception ignore) {}
        // 2) "2025-09-09 15:30:00"
        try { return LocalDateTime.parse(s, D_DATETIME_1); } catch (Exception ignore) {}
        // 3) "2025-09-09T15:30:00"
        try { return LocalDateTime.parse(s, D_DATETIME_2); } catch (Exception ignore) {}
        // 마지막 시도: ISO_LOCAL_DATE_TIME (초/밀리초 유연)
        try { return LocalDateTime.parse(s); } catch (Exception ignore) {}
        throw new IllegalArgumentException("unsupported datetime format: " + s);
    }

    private static BigDecimal parseBig(String s) {
        if (s == null || s.isBlank()) return null;
        return new BigDecimal(s);
    }

    /* ===========================
       DTOs
       =========================== */
    @Getter
    @ToString
    public static class TwelveBar {
        private final LocalDateTime datetime;
        private final BigDecimal open;
        private final BigDecimal high;
        private final BigDecimal low;
        private final BigDecimal close;

        public TwelveBar(LocalDateTime datetime, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
            this.datetime = datetime;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
        }
    }

    @Getter
    @ToString
    public static class QuoteData {
        private final BigDecimal price;
        private final BigDecimal change;
        private final BigDecimal percentChange;
        private final BigDecimal previousClose;
        private final BigDecimal open;
        private final BigDecimal high;
        private final BigDecimal low;

        public QuoteData(BigDecimal price, BigDecimal change, BigDecimal percentChange,
                         BigDecimal previousClose, BigDecimal open, BigDecimal high, BigDecimal low) {
            this.price = price;
            this.change = change;
            this.percentChange = percentChange;
            this.previousClose = previousClose;
            this.open = open;
            this.high = high;
            this.low = low;
        }
    }

    /* ==== JSON 매핑 ==== */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TimeSeriesResponse {
        public String status;          // "ok" or "error"
        public String message;         // when error
        public String code;            // when error
        public List<TsEntry> values;   // when ok
    }

    // ⚠️ Spring @Value 와의 이름 충돌 방지: 'Value' 대신 'TsEntry'
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TsEntry {
        public String datetime;
        public String open;
        public String high;
        public String low;
        public String close;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class QuoteResp {
        public String status;   // optional
        public String message;  // optional
        public String code;     // optional

        public String symbol;
        public String name;

        public String price;
        public String change;

        @JsonProperty("percent_change")
        public String percentChange;

        @JsonProperty("previous_close")
        public String previousClose;

        public String open;
        public String high;
        public String low;
    }
}
