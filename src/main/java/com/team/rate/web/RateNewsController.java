package com.team.rate.web;

import com.team.rate.dto.RateNewsItem;
import com.team.rate.service.RateNewsKey;
import com.team.rate.service.RateNewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class RateNewsController {

    private final RateNewsService newsService;

    /* ===== 진단용 핑 ===== */
    @ResponseBody
    @GetMapping("/rate/frag/news/ping")
    public String ping() {
        return "OK";
    }

    /** 사이드바 데모 페이지 (키 또는 심볼로 로딩) */
    @GetMapping("/rate/news-sidebar")
    public String newsSidebarPage(@RequestParam(required = false) RateNewsKey key,
                                  @RequestParam(required = false) String symbol,
                                  Model model) {
        model.addAttribute("key", key);
        model.addAttribute("symbol", symbol);
        return "rate/rate_news_sidebar";
    }

    /** 간단 뉴스 리스트 페이지 (번호/제목/시간 + 구분선 표기) */
    @GetMapping("/rate/news-list")
    public String newsListPage(@RequestParam(defaultValue = "samsung") String key,
                               @RequestParam(defaultValue = "10") int limit,
                               Model model) {
        model.addAttribute("key", key);
        model.addAttribute("limit", Math.max(1, Math.min(limit, 50)));
        return "rate/rate_news_list";
    }

    /** 자유 검색(JSON) */
    @ResponseBody
    @GetMapping(value = "/rate/api/news", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RateNewsItem> api(@RequestParam String query,
                                  @RequestParam(defaultValue = "10") int limit) {
        return newsService.searchNaverNews(query, Math.max(1, Math.min(limit, 50)));
    }

    /** 프리셋 키(JSON) - PathVariable */
    @ResponseBody
    @GetMapping(value = "/rate/api/news/preset/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RateNewsItem> newsByKey(@PathVariable RateNewsKey key,
                                        @RequestParam(defaultValue = "10") int limit) {
        return newsService.searchPreset(key, Math.max(1, Math.min(limit, 50)));
    }

    /** 심볼/티커(JSON) */
    @ResponseBody
    @GetMapping(value = "/rate/api/news/bySymbol", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RateNewsItem> newsBySymbol(@RequestParam String symbol,
                                           @RequestParam(defaultValue = "10") int limit) {
        return newsService.searchBySymbol(symbol, Math.max(1, Math.min(limit, 50)));
    }

    /* ====================== HTML 스니펫 엔드포인트 ====================== */

    /** 프리셋 키 → HTML 스니펫 (PathVariable) */
    @GetMapping("/rate/frag/news/preset/{key}")
    public String fragNewsByKey(@PathVariable RateNewsKey key,
                                @RequestParam(defaultValue = "10") int limit,
                                Model model) {
        List<RateNewsItem> items = newsService.searchPreset(key, Math.max(1, Math.min(limit, 50)));
        sortAndRenumber(items);
        model.addAttribute("title", key.name());
        model.addAttribute("items", items);
        return "rate/rate_news_panel";
    }

    /** 프리셋 키 → HTML 스니펫 (QueryParam) */
    @GetMapping("/rate/frag/news/preset")
    public String fragNewsByKeyQuery(@RequestParam String key,
                                     @RequestParam(defaultValue = "10") int limit,
                                     Model model) {
        // RateNewsService에 있는 심볼 매핑 재사용: 우선 Enum 변환 시도
        RateNewsKey ek = null;
        try { ek = RateNewsKey.valueOf(key.trim().toUpperCase()); } catch (Exception ignore) {}
        List<RateNewsItem> items = (ek != null)
                ? newsService.searchPreset(ek, Math.max(1, Math.min(limit, 50)))
                : newsService.searchNaverNews(key, Math.max(1, Math.min(limit, 50))); // 폴백
        sortAndRenumber(items);
        model.addAttribute("title", key);
        model.addAttribute("items", items);
        return "rate/rate_news_panel";
    }

    /** 심볼/티커 → HTML 스니펫 */
    @GetMapping("/rate/frag/news/bySymbol")
    public String fragNewsBySymbol(@RequestParam String symbol,
                                   @RequestParam(defaultValue = "10") int limit,
                                   Model model) {
        List<RateNewsItem> items = newsService.searchBySymbol(symbol, Math.max(1, Math.min(limit, 50)));
        sortAndRenumber(items);
        model.addAttribute("title", symbol.toUpperCase());
        model.addAttribute("items", items);
        return "rate/rate_news_panel";
    }

    /* 번호 오름차순 정렬 + 1..n 재부여 */
    private void sortAndRenumber(List<RateNewsItem> items) {
        if (items == null) return;
        items.sort(Comparator.comparingInt(it -> it.getNo() == 0 ? Integer.MAX_VALUE : it.getNo()));
        for (int i = 0; i < items.size(); i++) items.get(i).setNo(i + 1);
    }
}
