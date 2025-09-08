package com.team.rate.web;

import com.team.rate.dto.RateNewsItem;
import com.team.rate.service.RateNewsKey;
import com.team.rate.service.RateNewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class RateNewsController {

    private final RateNewsService newsService;

    /* ===== 차트 옆 HTML 스니펫 (제너릭: code 또는 symbol) ===== */
    @GetMapping("/rate/frag/news/forChart")
    public String fragNewsForChart(@RequestParam(required = false) String code,
                                   @RequestParam(required = false) String symbol,
                                   @RequestParam(defaultValue = "10") int limit,
                                   Model model) {
        String id = (code != null && !code.isBlank()) ? code : (symbol != null ? symbol : "");
        List<RateNewsItem> items = newsService.searchForChart(id, limit);
        sortAndRenumber(items);
        model.addAttribute("title", id == null ? "" : id.toUpperCase());
        model.addAttribute("items", items);
        return "rate/rate_news_panel";
    }

    /* ===== (편의) 프리셋 키 → HTML 스니펫 ===== */
    @GetMapping("/rate/frag/news/preset/{key}")
    public String fragNewsByKey(@PathVariable RateNewsKey key,
                                @RequestParam(defaultValue = "10") int limit,
                                Model model) {
        List<RateNewsItem> items = newsService.searchPreset(key, limit);
        sortAndRenumber(items);
        model.addAttribute("title", key.name());
        model.addAttribute("items", items);
        return "rate/rate_news_panel";
    }

    /* ===== 메인: 경제 뉴스 15개 ===== */
    @GetMapping("/rate/news-economy")
    public String newsEconomyPage(@RequestParam(defaultValue = "15") int limit, Model model) {
        List<RateNewsItem> items = newsService.searchEconomy(limit);
        sortAndRenumber(items);
        model.addAttribute("title", "ECONOMY");
        model.addAttribute("items", items);
        return "rate/rate_news_main";
    }

    @GetMapping("/rate/frag/news/economy")
    public String fragNewsEconomy(@RequestParam(defaultValue = "15") int limit, Model model) {
        List<RateNewsItem> items = newsService.searchEconomy(limit);
        sortAndRenumber(items);
        model.addAttribute("title", "ECONOMY");
        model.addAttribute("items", items);
        return "rate/rate_news_panel";
    }

    /* ===== 메인: 여러 종목을 한 화면에 (대시보드) ===== */
    @GetMapping("/rate/news-dashboard")
    public String newsDashboard(Model model,
                                @RequestParam(defaultValue = "10") int perSection) {
        List<RateNewsKey> keys = List.of(
                RateNewsKey.SAMSUNG, RateNewsKey.SKHYNIX, RateNewsKey.LGES,
                RateNewsKey.APPLE, RateNewsKey.NVIDIA,
                RateNewsKey.BITCOIN, RateNewsKey.ETHEREUM, RateNewsKey.RIPPLE,
                RateNewsKey.DOGE, RateNewsKey.SOLANA
        );

        List<Section> sections = new ArrayList<>();
        for (RateNewsKey k : keys) {
            List<RateNewsItem> items = newsService.searchPreset(k, perSection);
            sortAndRenumber(items);
            sections.add(new Section(k.name(), items));
        }
        model.addAttribute("sections", sections);
        model.addAttribute("perSection", perSection);
        return "rate/rate_news_dashboard";
    }

    /* ===== JSON (필요 시 Ajax 용) ===== */
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

    /* ===== 내부 유틸 ===== */
    private void sortAndRenumber(List<RateNewsItem> items) {
        if (items == null) return;
        items.sort(Comparator.comparingInt(it -> it.getNo() == 0 ? Integer.MAX_VALUE : it.getNo()));
        for (int i = 0; i < items.size(); i++) items.get(i).setNo(i + 1);
    }

    /** 대시보드 섹션 모델 */
    public record Section(String title, List<RateNewsItem> items) { }
}
