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
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class RateNewsController {

    private final RateNewsService newsService;

    /* =========================
     * 메인: 경제 뉴스 15개 + 종목 바로가기
     * ========================= */
    @GetMapping("/rate/news")                // 메인 진입
    public String newsHome(@RequestParam(defaultValue = "15") int limit, Model model) {
        List<RateNewsItem> items = newsService.searchEconomy(limit);
        sortAndRenumber(items);

        // 화면에 보여줄 프리셋(표시명)
        Map<RateNewsKey, String> display = Map.of(
                RateNewsKey.SAMSUNG,  "삼성전자 (005930)",
                RateNewsKey.SKHYNIX,  "SK하이닉스 (000660)",
                RateNewsKey.LGES,     "LG에너지솔루션 (373220)",
                RateNewsKey.APPLE,    "Apple (RBAQAAPL)",
                RateNewsKey.NVIDIA,   "NVIDIA (RBAQNVDA)",
                RateNewsKey.BITCOIN,  "비트코인 (BTC/KRW)",
                RateNewsKey.ETHEREUM, "이더리움 (ETH/KRW)",
                RateNewsKey.RIPPLE,   "리플 (XRP/KRW)",
                RateNewsKey.DOGE,     "도지 (DOGE/KRW)",
                RateNewsKey.SOLANA,   "솔라나 (SOL/KRW)"
        );

        model.addAttribute("economyItems", items);
        model.addAttribute("displayNames", display);
        model.addAttribute("keys", display.keySet()); // 반복용
        return "rate/rate_news_home";
    }

    // 기존 /rate/news-economy로 들어와도 동일 화면 제공
    @GetMapping("/rate/news-economy")
    public String newsEconomyAlias(@RequestParam(defaultValue = "15") int limit, Model model) {
        return newsHome(limit, model);
    }

    /* =========================
     * 종목 단일 페이지 (PathVariable로 프리셋 지정)
     * ========================= */
    @GetMapping("/rate/news/{key}")
    public String newsByKeyPage(@PathVariable RateNewsKey key,
                                @RequestParam(defaultValue = "10") int limit,
                                Model model) {
        List<RateNewsItem> items = newsService.searchPreset(key, limit);
        sortAndRenumber(items);
        model.addAttribute("title", key.name());
        model.addAttribute("items", items);
        return "rate/rate_news_list";
    }

    /* =========================
     * 종목 단일 페이지 (code 또는 symbol로 호출)
     * 예) /rate/news/by?code=005930  또는  /rate/news/by?symbol=BTC/KRW
     * ========================= */
    @GetMapping("/rate/news/by")
    public String newsByCodeOrSymbol(@RequestParam(required = false) String code,
                                     @RequestParam(required = false) String symbol,
                                     @RequestParam(defaultValue = "10") int limit,
                                     Model model) {
        String id = (code != null && !code.isBlank()) ? code : (symbol != null ? symbol : "");
        List<RateNewsItem> items = newsService.searchForChart(id, limit);
        sortAndRenumber(items);
        model.addAttribute("title", id == null ? "" : id.toUpperCase());
        model.addAttribute("items", items);
        return "rate/rate_news_list";
    }

    /* =========================
     * (유지) JSON 테스트용 - Ajax 안써도 무방
     * ========================= */
    @ResponseBody
    @GetMapping(value = "/rate/api/news/preset/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RateNewsItem> newsByKeyApi(@PathVariable RateNewsKey key,
                                           @RequestParam(defaultValue = "10") int limit) {
        return newsService.searchPreset(key, limit);
    }

    @ResponseBody
    @GetMapping(value = "/rate/api/news/bySymbol", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RateNewsItem> newsBySymbolApi(@RequestParam String symbol,
                                              @RequestParam(defaultValue = "10") int limit) {
        return newsService.searchForChart(symbol, limit);
    }

    @ResponseBody
    @GetMapping(value = "/rate/api/news/economy", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RateNewsItem> apiNewsEconomy(@RequestParam(defaultValue = "15") int limit) {
        return newsService.searchEconomy(limit);
    }

    /* 번호 오름차순 + 1..n 재부여 */
    private void sortAndRenumber(List<RateNewsItem> items) {
        if (items == null) return;
        items.sort(Comparator.comparingInt(it -> it.getNo() == 0 ? Integer.MAX_VALUE : it.getNo()));
        for (int i = 0; i < items.size(); i++) items.get(i).setNo(i + 1);
    }
}
