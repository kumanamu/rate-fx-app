package com.team.rate.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RateNaverSearchClient {

    private final RestTemplate restTemplate;

    @Value("${naver.search.news-url}")
    private String newsUrl;

    @Value("${naver.search.client-id}")
    private String clientId;

    @Value("${naver.search.client-secret}")
    private String clientSecret;

    /**
     * 네이버 뉴스 검색 API 호출
     * @param query 검색어
     * @param display 1~100 (API 제한)
     * @param start   시작 위치(1~1000) - 기본 1
     * @param sort    sim(정확도) | date(날짜 내림차순)
     */
    public Response searchNews(String query, int display, int start, String sort) {
        String url = UriComponentsBuilder.fromHttpUrl(newsUrl)
                .queryParam("query", query)
                .queryParam("display", Math.max(1, Math.min(display, 100)))
                .queryParam("start", Math.max(1, Math.min(start, 1000)))
                .queryParam("sort", (sort == null || sort.isBlank()) ? "date" : sort)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Naver-Client-Id", clientId);
        headers.set("X-Naver-Client-Secret", clientSecret);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Response> resp = restTemplate.exchange(url, HttpMethod.GET, entity, Response.class);
        return resp.getBody();
    }

    /* ==== JSON DTO ==== */

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        private String lastBuildDate;
        private int total;
        private int start;
        private int display;
        private List<Item> items;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String title;        // <b>강조</b> 태그 포함
        private String originallink;
        private String link;
        private String description;  // 요약
        private String pubDate;      // RFC 822 e.g. Tue, 09 Sep 2025 16:20:00 +0900
    }
}
