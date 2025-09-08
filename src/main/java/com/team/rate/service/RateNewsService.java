package com.team.rate.service;

import com.team.rate.config.NaverApiProperties;
import com.team.rate.dto.RateNewsItem;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;

@Slf4j
@Service
public class RateNewsService {

    private final RestClient http;
    private final NaverApiProperties props;

    public RateNewsService(NaverApiProperties props) {
        this.props = props;
        this.http = RestClient.create(); // full URL로 호출할 것이므로 baseUrl 불필요
    }

    // ===== 프리셋(동의어/보조키워드) =====
    private static final Map<RateNewsKey, List<String>> PRESET_SYNONYMS = Map.ofEntries(
            Map.entry(RateNewsKey.SAMSUNG,  List.of("삼성전자", "005930", "삼성전자 주가", "삼성전자 뉴스")),
            Map.entry(RateNewsKey.SKHYNIX,  List.of("SK하이닉스", "000660", "하이닉스", "SK하이닉스 주가", "SK하이닉스 뉴스")),
            Map.entry(RateNewsKey.LGES,     List.of("LG에너지솔루션", "373220", "LG에너지", "LG에너지솔루션 주가", "LG에너지솔루션 뉴스")),
            Map.entry(RateNewsKey.APPLE,    List.of("애플", "AAPL", "Apple", "애플 주가", "애플 뉴스")),
            Map.entry(RateNewsKey.NVIDIA,   List.of("엔비디아", "NVDA", "NVIDIA", "엔비디아 주가", "엔비디아 뉴스")),
            Map.entry(RateNewsKey.BITCOIN,  List.of("비트코인", "BTC", "BTC/KRW", "비트코인 시세", "비트코인 뉴스")),
            Map.entry(RateNewsKey.ETHEREUM, List.of("이더리움", "ETH", "ETH/KRW", "이더리움 시세", "이더리움 뉴스")),
            Map.entry(RateNewsKey.RIPPLE,   List.of("리플", "XRP", "XRP/KRW", "리플 시세", "리플 뉴스")),
            Map.entry(RateNewsKey.DOGE,     List.of("도지코인", "도지", "DOGE", "DOGE/KRW", "도지코인 시세", "도지코인 전망", "도지코인 뉴스")),
            Map.entry(RateNewsKey.SOLANA,   List.of("솔라나", "SOL", "SOL/KRW", "솔라나 시세", "솔라나 뉴스"))
    );
    private static final List<String> SUFFIXES = List.of("", " 시세", " 전망", " 뉴스");
    private static final int MAX_EXPANDED_QUERIES = 8;

    private static int perQueryLimit(int totalLimit, int queryCount) {
        int base = (int) Math.ceil((totalLimit * 1.5) / Math.max(1, queryCount));
        return Math.max(3, Math.min(base, totalLimit));
    }

    // ===== 공개 API (컨트롤러에서 호출) =====
    public List<RateNewsItem> searchEconomy(int limit) {
        List<String> topics = List.of("경제", "금리", "환율", "연준", "코스피", "나스닥", "원달러", "수출", "물가", "고용");
        return fanoutMerge(topics, limit);
    }

    public List<RateNewsItem> searchPreset(RateNewsKey key, int limit) {
        List<String> expanded = expandQueriesByKey(key);
        return fanoutMerge(expanded, limit);
    }

    public List<RateNewsItem> searchForChart(String id, int limit) {
        if (id == null || id.isBlank()) return List.of();
        return fanoutMerge(List.of(id, id + " 뉴스"), limit);
    }

    // ===== 팬아웃 병합/중복제거 =====
    private List<RateNewsItem> fanoutMerge(List<String> queries, int totalLimit) {
        if (queries == null || queries.isEmpty()) return List.of();

        List<String> use = queries.size() > MAX_EXPANDED_QUERIES
                ? queries.subList(0, MAX_EXPANDED_QUERIES) : queries;
        int perLimit = perQueryLimit(totalLimit, use.size());

        Map<String, RateNewsItem> dedup = new LinkedHashMap<>();
        for (String q : use) {
            try {
                List<RateNewsItem> one = searchOnce(q, perLimit);
                for (RateNewsItem it : one) {
                    if (it == null) continue;
                    String key = dedupKey(it);
                    dedup.putIfAbsent(key, it);
                    if (dedup.size() >= totalLimit * 2) break;
                }
            } catch (Exception ex) {
                log.warn("news search failed for '{}': {}", q, ex.toString());
            }
            if (dedup.size() >= totalLimit * 2) break;
        }

        List<RateNewsItem> out = dedup.values().stream().limit(totalLimit).collect(Collectors.toList());
        for (int i = 0; i < out.size(); i++) out.get(i).setNo(i + 1);
        return out;
    }

    private String dedupKey(RateNewsItem it) {
        String link = safe(it.getLink());
        if (!link.isEmpty()) return normalizeLink(link);
        String title = safe(it.getTitle());
        return title.isEmpty() ? UUID.randomUUID().toString() : title;
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    private String normalizeLink(String url) {
        try {
            URI u = new URI(url);
            String q = u.getQuery();
            if (q == null || q.isEmpty()) return url;
            String kept = Arrays.stream(q.split("&"))
                    .filter(p -> {
                        String k = p.split("=", 2)[0].toLowerCase(Locale.ROOT);
                        return !(k.startsWith("utm_") || k.equals("gs") || k.equals("m"));
                    })
                    .collect(Collectors.joining("&"));
            return new URI(u.getScheme(), u.getAuthority(), u.getPath(),
                    kept.isEmpty() ? null : kept, u.getFragment()).toString();
        } catch (URISyntaxException e) {
            return url;
        }
    }

    private List<String> expandQueriesByKey(RateNewsKey key) {
        List<String> base = PRESET_SYNONYMS.getOrDefault(key, List.of(key.name()));
        List<String> out = new ArrayList<>();
        for (String b : base) {
            for (String sfx : SUFFIXES) {
                String q = (b + sfx).trim();
                if (!out.contains(q)) out.add(q);
                if (out.size() >= MAX_EXPANDED_QUERIES) break;
            }
            if (out.size() >= MAX_EXPANDED_QUERIES) break;
        }
        return out;
    }

    // ===== 실제 단일 호출 (Naver OpenAPI) =====
    protected List<RateNewsItem> searchOnce(String query, int limit) {
        if (!props.hasKeys()) {
            log.warn("Naver API keys missing. Set naver.search.client-id / client-secret (또는 naver.api.* / 환경변수)");
            return List.of();
        }

        String endpoint = props.getNewsUrl(); // ex) https://openapi.naver.com/v1/search/news.json
        // ✅ 한글 쿼리 인코딩 확실히
        URI uri = org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(endpoint)
                .queryParam("query", query)
                .queryParam("display", Math.max(1, Math.min(limit, 30)))
                .queryParam("start", 1)
                .queryParam("sort", "sim")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();

        try {
            NaverNewsResp resp = http.get()
                    .uri(uri)
                    .header("X-Naver-Client-Id", props.getClientId())
                    .header("X-Naver-Client-Secret", props.getClientSecret())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(NaverNewsResp.class);

            if (resp == null || resp.items == null || resp.items.isEmpty()) return List.of();

            List<RateNewsItem> out = new ArrayList<>();
            for (NaverItem it : resp.items) {
                String title = stripTags(safe(it.title));
                String link  = safe(it.link);
                String time  = safe(it.pubDate);
                if (title.isEmpty() || link.isEmpty()) continue;

                RateNewsItem row = new RateNewsItem();
                row.setNo(0);
                row.setTitle(title);
                row.setLink(link);
                row.setTime(time);
                out.add(row);
            }
            return out;
        } catch (Exception ex) {
            // 여기서 삼키지 말고 로깅
            log.warn("naver news call failed. uri={}, err={}", uri, ex.toString());
            return List.of();
        }
    }

    /** 🔎 원인 파악용: 상태코드/본문을 그대로 돌려줌 */
    public Map<String, Object> debugRaw(String query, int limit) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("hasKeys", props.hasKeys());
        res.put("newsUrl", props.getNewsUrl());
        if (!props.hasKeys()) return res;

        URI uri = org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(props.getNewsUrl())
                .queryParam("query", query)
                .queryParam("display", Math.max(1, Math.min(limit, 30)))
                .queryParam("start", 1)
                .queryParam("sort", "sim")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
        res.put("uri", uri.toString());

        try {
            ResponseEntity<String> entity = http.get()
                    .uri(uri)
                    .header("X-Naver-Client-Id", props.getClientId())
                    .header("X-Naver-Client-Secret", props.getClientSecret())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(String.class);
            res.put("status", entity.getStatusCode().value());
            res.put("ok", entity.getStatusCode().is2xxSuccessful());
            res.put("body", entity.getBody());
        } catch (Exception ex) {
            res.put("error", ex.toString());
        }
        return res;
    }

    private static String stripTags(String s) {
        if (s == null) return "";
        String noTags = s.replaceAll("<[^>]+>", "");
        return noTags.replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    // ===== 응답 DTO =====
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NaverNewsResp { public List<NaverItem> items; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NaverItem {
        public String title;
        public String link;
        public String pubDate;
    }
}
