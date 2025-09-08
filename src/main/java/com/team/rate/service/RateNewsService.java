package com.team.rate.service;

import com.team.rate.api.RateNaverSearchClient;
import com.team.rate.dto.RateNewsItem;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

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
            Map.entry(RateNewsKey.ETHEREUM, "이더리움"),
            Map.entry(RateNewsKey.DOGE,     "도지코인"),
            Map.entry(RateNewsKey.RIPPLE,   "리플"),
            Map.entry(RateNewsKey.BITCOIN,  "비트코인"),
            Map.entry(RateNewsKey.SOLANA,   "솔라나")
    );

    /** 기존 자유 검색 (필요 시 유지) */
    public List<RateNewsItem> searchNaverNews(String query, int limit) {
        var res = naverClient.searchNews(query, Math.max(1, Math.min(limit, 50)), 1, "date");
        return convert(res, limit);
    }

    /** 프리셋 키로 검색 (캐시: 10분) */
    @Cacheable(value = "newsByKey", key = "#key.name() + '-' + #limit")
    public List<RateNewsItem> searchPreset(RateNewsKey key, int limit) {
        String query = QUERY.getOrDefault(key, null);
        if (query == null) return List.of();
        var res = naverClient.searchNews(query, Math.max(1, Math.min(limit, 50)), 1, "date");
        return convert(res, limit);
    }

    /** 심볼/티커에서 프리셋 키로 매핑 후 검색 */
    public List<RateNewsItem> searchBySymbol(String symbol, int limit) {
        RateNewsKey key = fromSymbol(symbol);
        if (key == null) return List.of();
        return searchPreset(key, limit);
    }

    /** 차트의 심볼/티커 → 프리셋 키 */
    public RateNewsKey fromSymbol(String symbol) {
        if (symbol == null) return null;
        String s = symbol.trim().toUpperCase(Locale.ROOT);

        // 한국 주식(예시): 종목코드 or 영문 키워드
        switch (s) {
            case "005930": case "SSNLF": case "SAMSUNG": case "SAMSUNG ELECTRONICS":
                return RateNewsKey.SAMSUNG;
            case "000660": case "HYNIX": case "SKHYNIX": case "SK HYNIX":
                return RateNewsKey.SKHYNIX;
            case "373220": case "LGES": case "LG ENERGY": case "LG ENERGY SOLUTION":
                return RateNewsKey.LGES;

            // 미국 주식
            case "AAPL":  return RateNewsKey.APPLE;
            case "NVDA":  return RateNewsKey.NVIDIA;

            // 크립토
            case "BTC": case "BTC-USD": case "XBT": return RateNewsKey.BITCOIN;
            case "ETH": case "ETH-USD":             return RateNewsKey.ETHEREUM;
            case "XRP": case "XRP-USD":             return RateNewsKey.RIPPLE;
            case "DOGE": case "DOGE-USD":           return RateNewsKey.DOGE;
            case "SOL": case "SOL-USD":             return RateNewsKey.SOLANA;

            default: return null;
        }
    }

    /* 내부: 네이버 응답을 화면 DTO로 변환 */
    private List<RateNewsItem> convert(RateNaverSearchClient.Response res, int limit) {
        List<RateNewsItem> out = new ArrayList<>();
        if (res == null || res.getItems() == null) return out;

        int no = 1;
        for (var it : res.getItems()) {
            String title = cleanTitle(it.getTitle());
            String link = (it.getLink() != null && !it.getLink().isBlank())
                    ? it.getLink()
                    : it.getOriginallink();

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

    /** 네이버가 주는 <b>…</b> 등 강조 태그 제거 */
    private String cleanTitle(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("<[^>]+>", ""); // 태그 제거
    }
}
