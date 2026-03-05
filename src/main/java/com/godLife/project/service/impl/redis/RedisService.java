package com.godLife.project.service.impl.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

  @Autowired
  private RedisTemplate<String, String> redisTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  // JavaType 캐시: 반복 생성으로 인한 GC 압박 감소
  private final ConcurrentHashMap<Class<?>, JavaType> typeCache = new ConcurrentHashMap<>();

  // Caffeine 로컬 캐시: Redis 네트워크 왕복 감소 (TTL 5분, 최대 500개)
  private final Cache<String, String> localCache = Caffeine.newBuilder()
      .maximumSize(500)
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .build();

  // 데이터 저장
  public void saveStringData(String key, String value, char type, long timeout) {

    Duration duration = switch (Character.toLowerCase(type)) {
      case 's' -> Duration.ofSeconds(timeout);
      case 'm' -> Duration.ofMinutes(timeout);
      case 'h' -> Duration.ofHours(timeout);
      case 'd' -> Duration.ofDays(timeout);
      default -> null;
    };

    if (duration != null) {
      redisTemplate.opsForValue().set(key, value, duration);
    } else {
      redisTemplate.opsForValue().set(key, value);
    }
  }

  // 리스트 DTO 저장
  public <T> void saveListData(String key, List<T> dataList, char type, long timeout) {
    try {
      String json = objectMapper.writeValueAsString(dataList); // 직렬화

      Duration duration = switch (Character.toLowerCase(type)) {
        case 's' -> Duration.ofSeconds(timeout);
        case 'm' -> Duration.ofMinutes(timeout);
        case 'h' -> Duration.ofHours(timeout);
        case 'd' -> Duration.ofDays(timeout);
        default -> null;
      };

      if (duration != null) {
        redisTemplate.opsForValue().set(key, json, duration);
      } else {
        redisTemplate.opsForValue().set(key, json);
      }

      // 로컬 캐시도 즉시 갱신 → 관리자 수정 시 즉각 반영
      localCache.put(key, json);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("리스트 직렬화 오류", e);
    }
  }

  // 데이터 조회
  public String getStringData(String key) {
    return redisTemplate.opsForValue().get(key);
  }

  // 리스트 DTO 데이터 조회
  public <T> List<T> getListData(String key, Class<T> clazz) {
    // 1차: Caffeine 로컬 캐시 조회 (네트워크 왕복 없음)
    String json = localCache.getIfPresent(key);

    if (json == null) {
      // 2차: Redis 조회
      json = redisTemplate.opsForValue().get(key);
      if (json == null) return null;
      // 로컬 캐시에 저장
      localCache.put(key, json);
    }

    try {
      JavaType javaType = typeCache.computeIfAbsent(clazz,
          c -> objectMapper.getTypeFactory().constructCollectionType(List.class, c));
      return objectMapper.readValue(json, javaType);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("리스트 역직렬화 오류", e);
    }
  }


  // 데이터 삭제
  public void deleteData(String key) {
    redisTemplate.delete(key);
    localCache.invalidate(key);
  }

  // 키 존재 여부 확인
  public boolean checkExistsValue(String key) {
    return redisTemplate.hasKey(key);
  }

  // 레디스 큐 생성 (왼쪽)
  public void leftPushToRedisQueue(String queueKey, String whatIdx) {
    redisTemplate.opsForList().leftPush(queueKey, whatIdx);
  }

  // 레디스 큐 생성 (오른쪽)
  public void rightPushToRedisQueue(String queueKey, String whatIdx) {
    redisTemplate.opsForList().rightPush(queueKey, whatIdx);
  }

  // 레디스 큐에서 꺼내기 (timeoutSeconds = 0 이면 무한 대기)
  public String brPopFromRedisQueue(String queueKey, long timeoutSeconds) {
    return redisTemplate.opsForList().rightPop(queueKey, Duration.ofSeconds(timeoutSeconds));
  }
}
