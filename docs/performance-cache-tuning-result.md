# 캐싱 전략 도입 성능 비교 보고서

- **테스트 도구**: k6
- **테스트 일자**: 2026-03-05
- **VU(가상 사용자)**: 50
- **테스트 시간**: 약 50초
- **대상 엔드포인트**: `/api/categories/{topMenu, job, target, challenge, icon}`

---

## 1. 테스트 환경 구성

| 구분 | 설명 |
|------|------|
| Cache OFF | `CacheWarmUpRunner` 비활성화 → Redis 미사용 → DB 직접 조회 |
| Cache ON | Redis(Lettuce Pool) + Caffeine 로컬 캐시 + JavaType 캐싱 적용 |

---

## 2. 전체 HTTP 요청 지표 (`http_req_duration`)

| 지표 | Cache OFF | Cache ON | 개선율 |
|------|----------:|--------:|-------:|
| avg  | 4.41 ms   | 0.83 ms | **-81.2%** |
| med  | 3.62 ms   | 0.61 ms | **-83.1%** |
| p(90)| 7.32 ms   | 1.69 ms | **-76.9%** |
| p(95)| 9.51 ms   | 2.12 ms | **-77.7%** |
| p(99)| 15.57 ms  | 3.68 ms | **-76.4%** |
| max  | 452.11 ms | 134.03 ms | -70.4% |

---

## 3. 엔드포인트별 p99 비교

| 엔드포인트 | Cache OFF p99 | Cache ON p99 | 개선율 |
|-----------|-------------:|------------:|-------:|
| topMenu   | 21.68 ms | 5.04 ms | **-76.7%** |
| job       | 15.45 ms | 3.28 ms | **-78.8%** |
| target    | 11.79 ms | 3.67 ms | **-68.9%** |
| challenge | 10.50 ms | 3.12 ms | **-70.3%** |
| icon      | 10.11 ms | 3.34 ms | **-67.0%** |

---

## 4. 처리량 (Throughput)

| 지표 | Cache OFF | Cache ON | 변화 |
|------|----------:|--------:|-----:|
| 총 요청 수 | 19,285 | 19,950 | +665 |
| 초당 요청 수 | 383.3 req/s | 397.4 req/s | **+3.7%** |
| 총 이터레이션 | 3,857 | 3,990 | +133 |
| 에러 수 | 0 | 0 | - |

---

## 5. 응답 대기 시간 (`http_req_waiting` — 순수 서버 처리 시간)

| 지표 | Cache OFF | Cache ON | 개선율 |
|------|----------:|--------:|-------:|
| avg  | 4.28 ms   | 0.69 ms | **-83.9%** |
| med  | 3.50 ms   | 0.55 ms | **-84.3%** |
| p(99)| 15.25 ms  | 2.88 ms | **-81.1%** |

---

## 6. 핵심 요약

```
p99 기준 전체 요청: 15.57ms → 3.68ms  (-76.4%)
p99 기준 topMenu:   21.68ms → 5.04ms  (-76.7%)
avg 기준 전체 요청:  4.41ms → 0.83ms  (-81.2%)
처리량(req/s):      383.3  → 397.4   (+3.7%)
에러율:             0%     → 0%      (유지)
```

---

## 7. 적용된 튜닝 내용

### 7-1. Lettuce 커넥션 풀 (`application-secret.properties`)
```properties
spring.data.redis.lettuce.pool.max-active=20
spring.data.redis.lettuce.pool.max-idle=10
spring.data.redis.lettuce.pool.min-idle=5
spring.data.redis.lettuce.pool.max-wait=200ms
```
- 풀 미설정 시 매 요청마다 커넥션 획득 오버헤드 발생 → 커넥션 재사용으로 개선

### 7-2. Caffeine 로컬 캐시 (`RedisService.java`)
```
읽기: Caffeine hit  → 반환 (네트워크 0)
      Caffeine miss → Redis 조회 → Caffeine 저장 → 반환
쓰기: Redis 저장 + Caffeine 갱신 (즉시 일관성)
삭제: Redis 삭제 + Caffeine 무효화
```
- maxSize=500, expireAfterWrite=5분
- Redis 네트워크 왕복을 인메모리 조회로 대체

### 7-3. JavaType 캐싱 (`RedisService.java`)
```java
private final ConcurrentHashMap<Class<?>, JavaType> typeCache = new ConcurrentHashMap<>();

JavaType javaType = typeCache.computeIfAbsent(clazz,
    c -> objectMapper.getTypeFactory().constructCollectionType(List.class, c));
```
- 역직렬화 시 매 요청마다 `JavaType` 객체 생성 → GC 압박 감소

---

## 8. 비고

- Cache ON 기준 `max` 값(134ms)이 여전히 존재하나, 이는 JVM 워밍업 초기 또는 Caffeine 최초 miss 시 발생하는 일시적 스파이크로 추정됨.
- 기존 문제였던 p99 40~46ms 스파이크(Redis 캐시만 사용 시)는 Caffeine 로컬 캐시 도입으로 **3.68ms까지 감소**.
