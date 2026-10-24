package com.skycast.weather.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cross-cutting concern with Spring AOP: times every public service / client method,
 * publishes a Micrometer timer (skycast.method.duration) and logs slow calls and provider errors.
 */
@Aspect
@Component
public class PerformanceAspect {

    private static final Logger log = LoggerFactory.getLogger(PerformanceAspect.class);
    private static final long SLOW_MS = 1500;

    private final MeterRegistry meters;

    public PerformanceAspect(MeterRegistry meters) {
        this.meters = meters;
    }

    @Pointcut("execution(public * com.skycast.weather.service..*(..))")
    void serviceMethods() {
    }

    @Pointcut("execution(public * com.skycast.weather.client.OpenMeteoClient.*(..))")
    void providerCalls() {
    }

    @Around("serviceMethods() || providerCalls()")
    public Object time(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        String outcome = "success";
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            outcome = "error";
            throw t;
        } finally {
            long nanos = System.nanoTime() - start;
            String type = pjp.getSignature().getDeclaringType().getSimpleName();
            String method = pjp.getSignature().getName();
            Timer.builder("skycast.method.duration")
                    .tag("class", type).tag("method", method).tag("outcome", outcome)
                    .register(meters)
                    .record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
            if (nanos / 1_000_000 > SLOW_MS) {
                log.warn("Slow call {}.{} took {} ms", type, method, nanos / 1_000_000);
            }
        }
    }

    @AfterThrowing(pointcut = "providerCalls()", throwing = "error")
    public void providerFailed(JoinPoint jp, Throwable error) {
        log.warn("Open-Meteo call {} failed: {}", jp.getSignature().getName(), error.toString());
    }
}
