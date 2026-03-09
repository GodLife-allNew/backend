# Redis 캐싱 & 로깅 개선 작업 문서

> 작성일: 2026-03-05
> 대상 브랜치: `dev`
> 작업 배경: 부하테스트(k6) 결과 분석 및 코드 전체 검토 후 도출된 개선 사항

---

## 프로젝트 핵심 구조 (작업 전 필독)

```
캐싱 흐름:
  애플리케이션 기동
    → CacheWarmUpRunner (ApplicationRunner)
    → RedisService.saveListData() 로 8개 카테고리 캐시 적재 (TTL 없음)
    → 이후 요청은 CategoryController에서 Cache-Aside 패턴으로 처리

관련 핵심 파일:
  - runner/CacheWarmUpRunner.java          ← 워밍업
  - service/impl/redis/RedisService.java  ← Redis 추상화 서비스
  - controller/CategoryController.java    ← Cache-Aside 조회
  - service/impl/adminImpl/
      CompContentServiceImpl.java         ← 카테고리 수정/삭제 + 캐시 갱신
      CompSystemServiceImpl.java          ← FAQ/QNA 카테고리 삭제 + 확인 플래그 캐시
  - config/RedisConfig.java               ← RedisTemplate 설정
  - resources/config/logback-spring.xml   ← Logback 설정
```

> **삭제 완료 (2026-03-05)**
> 부하테스트 육안 확인용으로 임시 추가했던 코드를 제거함:
> - `common/anotation/LogExecutionTime.java` (삭제)
> - `common/aop/ResponseTimeAspect.java` (삭제)
> - `CategoryController.java` 내 `@LogExecutionTime` 어노테이션 및 관련 import (제거)

---

## 개선 항목 (우선순위 순)

---

### [P1] 캐시 미스 시 자동 재캐싱 추가

**파일**: `src/main/java/com/godLife/project/controller/CategoryController.java`

**문제**:
캐시 미스(Redis 장애, FLUSHALL 등) 발생 시 DB 조회 결과를 Redis에 다시 저장하지 않음.
복구 후 다음 WarmUpRunner 실행 전까지 모든 요청이 DB로 직접 향함.

**현재 코드 패턴** (캐시를 쓰는 8개 메서드 전부 동일):
```java
public List<TopMenu> topMenu() {
    List<TopMenu> data = redisService.getListData("category::topMenu", TopMenu.class);
    if (data == null || data.isEmpty()) {
        return categoryService.getProcessedAllTopCategories(); // Redis에 저장 안 함
    }
    return data;
}
```

**수정 후 패턴**:
```java
public List<TopMenu> topMenu() {
    List<TopMenu> data = redisService.getListData("category::topMenu", TopMenu.class);
    if (data == null || data.isEmpty()) {
        data = categoryService.getProcessedAllTopCategories();
        redisService.saveListData("category::topMenu", data, 'n', 0); // 재캐싱 추가
    }
    return data;
}
```

**수정 대상 메서드 목록** (CategoryController.java 내 전부):

| 메서드 | 캐시 키 | Service 메서드 | DTO 타입 |
|---|---|---|---|
| `topMenu()` | `category::topMenu` | `getProcessedAllTopCategories()` | `TopMenu` |
| `job()` | `category::job` | `getAllJobCategories()` | `JobCateDTO` |
| `target()` | `category::target` | `getAllTargetCategories()` | `TargetCateDTO` |
| `challenge()` | `category::chall` | `getAllChallCategories()` | `ChallengeCateDTO` |
| `icon()` | `category::userIcon` | `getUserIconInfos()` | `IconDTO` |
| `iconAdmin()` | `category::adminIcon` | `getAllIconInfos()` | `IconDTO` |
| `fireInfos()` | `category::fire` | `getAllFireInfos()` | `FireDTO` |
| `userLevelInfos()` | `category::userLv` | `getAllUserLevelInfos()` | `UserLevelDTO` |

**검증**: 서버 기동 후 `redis-cli FLUSHALL` → 엔드포인트 호출 → `redis-cli KEYS category::*` 로 캐시 재적재 확인

---

### [P2] 트랜잭션 커밋 후 캐시 갱신 (불일치 방지)

**파일**: `src/main/java/com/godLife/project/service/impl/adminImpl/CompContentServiceImpl.java`

**문제**:
`@Transactional` 메서드 안에서 DB 업데이트와 캐시 갱신이 같은 순서로 실행됨.
DB 트랜잭션이 롤백되어도 이미 갱신된 Redis 캐시는 롤백되지 않아 불일치 발생.

**현재 코드**:
```java
@Transactional
public int updateTargetCategory(TargetCateDTO targetCateDTO) {
    int result = compContentMapper.updateTargetCategory(targetCateDTO);
    refreshTargetCache(); // 트랜잭션 커밋 전에 캐시 갱신 → 롤백 시 불일치
    return result;
}
```

**수정 방법 - Spring 이벤트 활용**:

1. **이벤트 클래스 생성**: `src/main/java/com/godLife/project/event/CacheRefreshEvent.java`
```java
package com.godLife.project.event;

public record CacheRefreshEvent(String cacheKey) {}
```

2. **이벤트 리스너 클래스 생성**: `src/main/java/com/godLife/project/event/CacheRefreshEventListener.java`
```java
package com.godLife.project.event;

import com.godLife.project.service.impl.adminImpl.CompContentServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class CacheRefreshEventListener {

    private final CompContentServiceImpl compContentService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCacheRefresh(CacheRefreshEvent event) {
        switch (event.cacheKey()) {
            case "category::target"  -> compContentService.refreshTargetCache();
            case "category::job"     -> compContentService.refreshJobCache();
            case "category::chall"   -> compContentService.refreshChallCache();
            // 필요 시 추가
        }
    }
}
```

3. **CompContentServiceImpl 수정**:
```java
// ApplicationEventPublisher 주입 추가
private final ApplicationEventPublisher eventPublisher;

@Transactional
public int updateTargetCategory(TargetCateDTO targetCateDTO) {
    int result = compContentMapper.updateTargetCategory(targetCateDTO);
    eventPublisher.publishEvent(new CacheRefreshEvent("category::target")); // 커밋 후 실행
    return result;
}
```

**수정 대상 메서드** (CompContentServiceImpl.java 내):

| 메서드 | 현재 즉시 갱신 메서드 | 이벤트 키 |
|---|---|---|
| `updateTargetCategory()` | `refreshTargetCache()` | `category::target` |
| `deleteTargetCategory()` | `refreshTargetCache()` | `category::target` |
| `updateJobCategory()` | `refreshJobCache()` | `category::job` |
| `deleteJobCategory()` | `refreshJobCache()` | `category::job` |
| `updateChallCategory()` | `refreshChallCache()` | `category::chall` |
| `deleteChallCategory()` | `refreshChallCache()` | `category::chall` |

**검증**: 관리자에서 카테고리 수정 후 DB 트랜잭션 강제 실패(예: 유효성 오류) 시 캐시가 갱신되지 않는지 확인

---

### [P3] MDC 필터 추가 (요청 추적 ID)

**문제**:
동시 요청이 많을 때 로그에서 특정 요청의 흐름(요청 → 서비스 → DB → 응답)을 추적 불가.

**신규 파일 생성**: `src/main/java/com/godLife/project/common/filter/MdcLoggingFilter.java`

```java
package com.godLife.project.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class MdcLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);
        MDC.put("method", request.getMethod());
        MDC.put("uri", request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear(); // 스레드 풀 재사용 시 오염 방지 - 반드시 clear
        }
    }
}
```

**logback-spring.xml 패턴 수정**:
파일: `src/main/resources/config/logback-spring.xml`

```xml
<!-- 기존 -->
<property name="LOG_PATTERN"
  value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"/>

<!-- 수정 후 -->
<property name="LOG_PATTERN"
  value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{requestId}] [%thread] %-5level %logger{36} - %msg%n"/>
```

**적용 후 로그 예시**:
```
2026-03-05 14:23:01.123 [a3f2b1c4] [http-nio-9090-exec-1] INFO  CategoryController - ...
2026-03-05 14:23:01.130 [a3f2b1c4] [http-nio-9090-exec-1] ERROR GlobalExceptionHandler - ...
```
→ `requestId`로 같은 요청의 로그를 grep으로 한 번에 추출 가능

**검증**: 서버 기동 후 API 호출 → `logs/app.log`에서 `[requestId]` 포함 여부 확인

---

## 작업 체크리스트

```
[ ] P1 - CategoryController: 캐시 미스 시 재캐싱 추가 (8개 메서드)
[ ] P2 - CacheRefreshEvent / CacheRefreshEventListener 클래스 생성
[ ] P2 - CompContentServiceImpl: eventPublisher 주입 + 트랜잭션 이벤트로 전환
[ ] P3 - MdcLoggingFilter 클래스 생성
[ ] P3 - logback-spring.xml 패턴에 %X{requestId} 추가
```

---

## 참고: 변경하지 않아도 되는 것

- `RedisConfig.java` - RedisTemplate 설정은 현재 적절함
- `CacheWarmUpRunner.java` - 워밍업 로직 자체는 문제 없음
- `QnaRedisKey.java` enum - 키 관리 방식 양호
- `CompSystemServiceImpl.java`의 확인 플래그 패턴 - 의도된 설계
- Logback 롤링 정책 (30일) - 적절함
