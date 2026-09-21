package com.example.CompetencyHub.aspect;

import com.example.CompetencyHub.config.AppProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;


/**
 * Times every service method and logs the slow ones.
 *
 * <p>This is what AOP is for: a concern that applies everywhere and belongs nowhere.
 * Timing code inside each service method would be identical, repeated, and easy to
 * forget on the one method that later turns out to be slow. Here it is written once
 * and applies to every @Service bean, including ones that do not exist yet.
 *
 * <p>Like a building's security cameras: the rooms do not know they are filmed, and
 * adding a room does not require installing a new logging system.
 *
 * <p>@ConditionalOnProperty means the bean is not created at all when
 * competencyhub.audit.enabled is false — no aspect, no proxying, no overhead. That is
 * cleaner than an if-check inside the advice, which still pays the interception cost.
 */
@Aspect
@Component
@ConditionalOnProperty(prefix = "competencyhub.audit", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ServiceTimingAspect {

    private static final Logger log = LoggerFactory.getLogger(ServiceTimingAspect.class);

    private final AppProperties properties;

    public ServiceTimingAspect(AppProperties properties) {
        this.properties = properties;
    }

    /**
     * Named pointcut: every method on any class annotated @Service.
     *
     * <p>Naming it, rather than repeating the expression in each advice, means the
     * definition of "what we instrument" exists once. An alternative expression,
     * {@code execution(* com.example.competencyhub.service..*(..))}, targets by
     * package instead — annotation-based is more robust to files moving.
     */
    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void serviceMethods() {
        // Marker method. The body is never executed; only the annotation matters.
    }

    /**
     * Around advice — the only kind that can see both sides of a call and control
     * whether it proceeds at all.
     *
     * <p>{@code proceed()} runs the real method. Forgetting to call it is the classic
     * around-advice bug: the target method silently never executes and the caller
     * gets null.
     */
    @Around("serviceMethods()")
    public Object timeServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();

        try {
            return joinPoint.proceed();
        } finally {
            // finally, not after the return: the timing should be recorded even when
            // the method throws. A slow failure is still slow.
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

            String target = joinPoint.getSignature().getDeclaringType().getSimpleName()
                    + "." + joinPoint.getSignature().getName();

            if (elapsedMs >= properties.getAudit().getSlowCallThresholdMs()) {
                log.warn("SLOW {} took {} ms", target, elapsedMs);
            } else {
                log.debug("{} took {} ms", target, elapsedMs);
            }
        }
    }

    /**
     * Logs exceptions escaping the service layer, with the arguments that caused them.
     *
     * <p>@AfterThrowing observes but cannot suppress — the exception continues to the
     * caller regardless. That is correct here: this is instrumentation, and an aspect
     * that swallowed exceptions would be a debugging nightmare.
     */
    @AfterThrowing(pointcut = "serviceMethods()", throwing = "ex")
    public void logServiceFailure(org.aspectj.lang.JoinPoint joinPoint, Throwable ex) {
        log.warn("{}.{} failed: {}",
                joinPoint.getSignature().getDeclaringType().getSimpleName(),
                joinPoint.getSignature().getName(),
                ex.getMessage());
    }
}