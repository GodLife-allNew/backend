package com.godLife.project.common.aop;

import lombok.extern.log4j.Log4j2;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Log4j2
// 메소드 응답 시간 로그 출력 aop
public class ResponseTimeAspect {

  @Around("@annotation(com.godLife.project.common.anotation.LogExecutionTime)")
  public Object logResponseTime(ProceedingJoinPoint joinPoint) throws Throwable {
    long start = System.currentTimeMillis();

    Object result = joinPoint.proceed(); // 실제 컨트롤러 메서드 실행

    long end = System.currentTimeMillis();

    log.info(
        "[{}] response time = {} ms",
        joinPoint.getSignature().toShortString(),
        (end - start)
    );

    return result;
  }


}
