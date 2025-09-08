package com.team.rate.service;

import com.team.rate.api.RateNaverSearchClient;
import com.team.rate.dto.RateNewsItem;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RateNewsService {

    private final RateNaverSearchClient naverClient;

    // 프리셋: 키 → 네이버 검색어
    private static final Map<RateNewsKey, String> QUERY = Map.ofEntries(
            Map.entry(RateNewsKey.SAMSUNG,  "삼성전자"),
            Map.entry(RateNewsKey.SKHYNIX,  "SK하이닉스"),
            Map.entry(RateNewsKey.LGES,     "LG에너지솔루션"),
            Map.entry(RateNewsKey.APPLE,    "Apple"),
            Map.entry(RateNewsKey.NVIDIA,   "NVIDIA"),
            Map.entry(RateNewsKey.BITCOIN,  "비트코인"),
            Map.entry(RateNewsKey.ETHEREUM, "이더리움"),
            Map.entry(RateNewsKey.RIPPLE,   "리플"),
            Map.entry(RateNewsKey.DOGE,     "도지코인"),
            Map.entry(RateNewsKey.SOLANA,   "솔라나")
    );

    /** 차트에서 코드/심볼을 던지면 자동 매핑해 N건 조회 */
    public List<RateNewsItem> searchForChart(String codeOrSymbol, int limit) {
        if (codeOrSymbol == null || codeOrSymbol.isBlank()) return List.of();
        RateNewsKey key = fromSymbol(codeOrSymbol);
        if (key != null) return searchPreset(key, limit);
        return searchNaverNews(codeOrSymbol, limit); // 폴백
    }

    /** 메인: 경제 뉴스 (여러 키워드 개별 호출 → 합치기 → 중복 제거 → 최신순 정렬) */
    @Cacheable(value = "newsEconomy", key = "'economy-v2-' + #limit")
    public List<RateNewsItem> searchEconomy(int limit) {
        int cap = cap(limit);

        // 효과가 좋았던 키워드 세트 (필요 시 가감 가능)
        List<String> keywords = List.of(
                "증시", "경제", "금리", "환율",
                "경기", "물가",
                "코스피", "코스닥",
                "미국증시", "나스닥",
                "연준", "기준금리"
        );

        // 합치기 + 중복제거
        List<ItemExt> bucket = new ArrayList<>();
        Set<String> seen = new HashSet<>(); // title+host 기준 중복 방지

        for (String q : keywords) {
            var res = naverClient.searchNews(q, 20, 1, "date"); // 키워드별 최대 20건
            if (res == null || res.getItems() == null) continue;

            for (var it : res.getItems()) {
                String title = cleanTitle(it.getTitle());
                String link = (it.getLink() != null && !it.getLink().isBlank())
                        ? it.getLink()
                        : it.getOriginallink();
                if (isBlank(title) || isBlank(link)) continue;

                String host = hostOf(link);
                String dedupKey = (title + "|" + host).toLowerCase(Locale.ROOT);
                if (!seen.add(dedupKey)) continue; // 이미 본 기사

                Instant ts = parsePub(it.getPubDate()); // 정렬용
                bucket.add(new ItemExt(title, link, it.getPubDate(), ts));
            }
        }

        // 최신순 정렬 → 상위 cap개
        bucket.sort(Comparator.comparing(ItemExt::ts).reversed());
        if (bucket.size() > cap) bucket = bucket.subList(0, cap);

        // DTO 변환 (번호는 컨트롤러에서 1..n으로 재부여하므로 여기선 0으로 둬도 OK)
        List<RateNewsItem> out = new ArrayList<>(bucket.size());
        int no = 1;
        for (ItemExt e : bucket) {
            out.add(RateNewsItem.builder()
                    .no(no++) // 바로 번호 붙여줌 (컨트롤러가 다시 1..n 부여해도 동일)
                    .title(e.title())
                    .link(e.link())
                    .time(e.pubDate() == null ? "-" : e.pubDate())
                    .build());
        }
        return out;
    }

    /** 프리셋 키로 검색 (캐시: 10분) */
    @Cacheable(value = "newsByKey", key = "#key.name() + '-' + #limit")
    public List<RateNewsItem> searchPreset(RateNewsKey key, int limit) {
        String query = QUERY.getOrDefault(key, null);
        if (query == null) return List.of();
        var res = naverClient.searchNews(query, cap(limit), 1, "date");
        return convert(res, limit);
    }

    /** 자유검색(보조) */
    public List<RateNewsItem> searchNaverNews(String query, int limit) {
        var res = naverClient.searchNews(query, cap(limit), 1, "date");
        return convert(res, limit);
    }

    private int cap(int limit) { return Math.max(1, Math.min(limit, 50)); }

    /** 코드/심볼 → 프리셋 키 매핑 */
    public RateNewsKey fromSymbol(String symbol) {
        if (symbol == null) return null;
        String s = symbol.trim().toUpperCase(Locale.ROOT);

        // 예: ETH/KRW → ETH
        int slash = s.indexOf('/');
        if (slash > 0) s = s.substring(0, slash);

        switch (s) {
            // 국내 주식 코드
            case "005930": return RateNewsKey.SAMSUNG;   // 삼성전자
            case "000660": return RateNewsKey.SKHYNIX;   // SK하이닉스
            case "373220": return RateNewsKey.LGES;      // LG에너지솔루션

            // 해외 주식 코드(요구 사양)
            case "RBAQAAPL":
            case "AAPL":   return RateNewsKey.APPLE;

            case "RBAQNVDA":
            case "NVDA":   return RateNewsKey.NVIDIA;

            // 크립토 심볼
            case "BTC":    return RateNewsKey.BITCOIN;
            case "ETH":    return RateNewsKey.ETHEREUM;
            case "XRP":    return RateNewsKey.RIPPLE;
            case "DOGE":   return RateNewsKey.DOGE;
            case "SOL":    return RateNewsKey.SOLANA;

            default: return null;
        }
    }

    /* 네이버 응답 → 화면 DTO */
    private List<RateNewsItem> convert(RateNaverSearchClient.Response res, int limit) {
        List<RateNewsItem> out = new ArrayList<>();
        if (res == null || res.getItems() == null) return out;

        int no = 1;
        for (var it : res.getItems()) {
            String title = cleanTitle(it.getTitle());
            String link = (it.getLink() != null && !it.getLink().isBlank())
                    ? it.getLink()
                    : it.getOriginallink();

            if (isBlank(title) || isBlank(link)) continue;

            out.add(RateNewsItem.builder()
                    .no(no++)
                    .title(title)
                    .link(link)
                    .time(it.getPubDate() == null ? "-" : it.getPubDate())
                    .build());
            if (out.size() >= limit) break;
        }
        return out;
    }

    /* ===== 유틸 ===== */

    private String cleanTitle(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("<[^>]+>", ""); // <b> 제거
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    private String hostOf(String url) {
        try { return URI.create(url).getHost(); } catch (Exception e) { return ""; }
    }

    private Instant parsePub(String pub) {
        if (pub == null || pub.isBlank()) return Instant.EPOCH;
        try {
            // 네이버 pubDate 예: "Mon, 08 Sep 2025 16:20:00 +0900"
            return ZonedDateTime.parse(pub, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception ignore) {
            return Instant.EPOCH;
        }
    }

    private record ItemExt(String title, String link, String pubDate, Instant ts) {}
}
