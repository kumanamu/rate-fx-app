package com.team.rate.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 차트 옆 뉴스 패널 테스트용 데모 컨트롤러
 */
@Controller
public class RateDemoController {

    /** 비트코인 전용 데모: 좌측 차트 자리, 우측 뉴스(iframe) */
    @GetMapping("/rate/demo/bitcoin")
    public String demoBitcoin(Model model) {
        model.addAttribute("title", "Bitcoin (BTC/KRW)");
        // ✅ 최신 엔드포인트로 변경: /rate/news/by
        model.addAttribute("iframeSrc", "/rate/news/by?symbol=BTC/KRW&limit=10");
        return "rate/demo_chart_news";
    }

    /**
     * 범용 데모: code 또는 symbol을 주면 해당 뉴스 패널을 붙여줌
     * 예) /rate/demo/chart-news?symbol=ETH/KRW
     *     /rate/demo/chart-news?code=005930
     */
    @GetMapping("/rate/demo/chart-news")
    public String demoGeneric(@RequestParam(required = false) String code,
                              @RequestParam(required = false) String symbol,
                              @RequestParam(defaultValue = "10") int limit,
                              Model model) {
        String title = (symbol != null && !symbol.isBlank()) ? symbol : (code != null ? code : "Unknown");
        String src;
        if (symbol != null && !symbol.isBlank()) {
            // ✅ 최신 엔드포인트로 변경
            src = "/rate/news/by?symbol=" + symbol + "&limit=" + limit;
        } else if (code != null && !code.isBlank()) {
            // ✅ 최신 엔드포인트로 변경
            src = "/rate/news/by?code=" + code + "&limit=" + limit;
        } else {
            // 기본값: BTC/KRW
            title = "Bitcoin (BTC/KRW)";
            src = "/rate/news/by?symbol=BTC/KRW&limit=10";
        }
        model.addAttribute("title", title);
        model.addAttribute("iframeSrc", src);
        return "rate/demo_chart_news";
    }
}
