package com.team.rate.service;

import com.team.rate.api.RateNaverSearchClient;
import com.team.rate.dto.RateNewsItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RateNewsService {

    private final RateNaverSearchClient naverClient;

    /**
     * 네이버 뉴스 검색 결과를 즉시 변환(서버 저장 없음)
     */
    public List<RateNewsItem> searchNaverNews(String query, int limit) {
        var res = naverClient.searchNews(query, Math.max(1, Math.min(limit, 50)), 1, "date");

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
        // 태그 제거
        String noTags = raw.replaceAll("<[^>]+>", "");
        // HTML 엔티티까지 깔끔히 하려면 commons-text 추가 후 unescapeHtml4 사용 가능
        return noTags;
    }
}
