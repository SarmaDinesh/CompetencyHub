package com.example.CompetencyHub.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ServiceTimingAspect {

    private static final Logger log = LoggerFactory.getLogger(ServiceTimingAspect.class);

    private final MeterRegistry meterRegistry;

    public ServiceTimingAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("execution(* com.example.CompetencyHub..*Service*.*(..))")
    public Object timeServiceCall(ProceedingJoinPoint pjp) throws Throwable {
        Timer.Sample sample = Timer.start(meterRegistry);
        String type = pjp.getSignature().getDeclaringType().getSimpleName();
        String method = pjp.getSignature().getName();
        String outcome = "success";

        try {
            return pjp.proceed();
        } catch (Throwable t) {
            outcome = "error";
            // Keep the log for failures. A failure is a discrete event someone will want
            // the detail of -- that is genuinely a logging question, not a metrics one.
            log.warn("{}.{} failed: {}", type, method, t.getMessage());
            throw t;
        } finally {
            /*
             * TAGS, not method names baked into the metric name. "service.call" with tags
             * {class=CourseServiceImpl, method=findAll, outcome=success} lets you ask for
             * p95 across all services, or one class, or only failures -- from one metric.
             *
             * Tag VALUES must be bounded. Never tag with a course id, a student id, or a
             * user-supplied string: every distinct value creates a new time series, and an
             * unbounded tag is how you take down your metrics backend. This is called a
             * cardinality explosion and it is the single most common Micrometer mistake.
             */
            sample.stop(Timer.builder("service.call")
                    .tag("class", type)
                    .tag("method", method)
                    .tag("outcome", outcome)
                    .description("Service layer method execution time")
                    .register(meterRegistry));
        }
    }
}
