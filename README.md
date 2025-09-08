
# rate-fx-app

Thymeleaf + Spring Boot + JPA + MySQL + Lombok으로 일별 환율(USD/KRW, JPY/KRW[100엔], EUR/KRW)의 현재가/등락률/등락금액을 표시하는 MVP.

## 실행
1) MySQL에 `fxapp` DB 생성 후 사용자(fx/fxpwd) 준비
2) `src/main/resources/application.properties`에서 DB 정보와 `twelvedata.apikey` 확인
3) IDE(인텔리제이)에서 Gradle 프로젝트로 열기
4) `http://localhost:8080/rate/admin/refresh` 로 데이터 수집
5) `http://localhost:8080/rate/fx/daily` 로 화면 확인

## 참고
- 패키지/클래스/뷰에 `rate` 접두사 사용
- 엔화는 화면 표시만 100엔 기준(*100) 처리
