package com.team.rate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 네이버 뉴스 검색 결과를 화면에 즉시 뿌리기 위한 DTO.
 * 서버 DB에는 저장하지 않음.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateNewsItem {
    /** 게시글 번호(오름차순 1..n) */
    private int no;

    /** 기사 제목 */
    private String title;

    /** 기사 링크 (네이버 or 원문 언론사) */
    private String link;

    /** 표기 시간 (네이버 pubDate 그대로) */
    private String time;
}
