# rate-fx-app · Refactor Pack (v3)

정리 목표
- `web` 패키지 제거 → `controller`로 통일
- `entity`/`repository` 추가 (DB 규칙: `asset_id` FK)
- 뉴스는 **asset_id → symbol/name** 매핑 후 기존 `RateNewsService` 사용 (로직 변경 없음)
- 템플릿 정리: 리스트/패널/데모/에러

## 적용 순서
1) 기존 프로젝트에서 **`src/main/java/com/team/rate/web` 패키지 전체 삭제**  
   (이 패치에 동일 컨트롤러가 `controller`로 제공됩니다)
2) 이 압축의 파일을 프로젝트 루트에 **덮어쓰기**
3) Gradle `clean` → 재실행
4) 확인
   - `/rate/news/by-asset/1`
   - `/rate/frag/news/forChart?assetId=1&limit=10` (iframe)
   - `/rate/news/by?code=005930` or `?symbol=BTC/KRW`
   - `/rate/news-economy` (메인 경제 뉴스)
   - 데모: `/rate/demo/bitcoin`, `/rate/demo/chart-news?code=005930`

## 포함 파일
- `src/main/java/com/team/rate/controller/RateNewsController.java`
- `src/main/java/com/team/rate/controller/RateDemoController.java`
- `src/main/java/com/team/rate/entity/Asset.java`
- `src/main/java/com/team/rate/entity/News.java`
- `src/main/java/com/team/rate/repository/AssetRepository.java`
- `src/main/java/com/team/rate/repository/NewsRepository.java`
- `src/main/resources/templates/rate/rate_news_panel.html`
- `src/main/resources/templates/rate/rate_news_list.html`
- `src/main/resources/templates/rate/rate_news_main.html`
- `src/main/resources/templates/rate/demo_chart_news.html`
- `src/main/resources/templates/error.html`
- `src/main/resources/templates/error/404.html`
- `src/main/resources/templates/error/5xx.html`

## 팀 규칙 (요약)
- 종목 식별은 `assets.asset_id`로 통일
- 컨트롤러는 `controller` 패키지에만 위치
- DB 스키마 예시
  - `assets(asset_id PK, symbol VARCHAR, name VARCHAR)`
  - `news(news_id PK, asset_id FK -> assets.asset_id, timestamp DATETIME, title VARCHAR, url_link VARCHAR)`
