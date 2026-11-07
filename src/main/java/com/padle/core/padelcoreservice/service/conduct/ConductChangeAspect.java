package com.padle.core.padelcoreservice.service.conduct;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
@RequiredArgsConstructor
public class ConductChangeAspect {

    private final PlatformTransactionManager transactionManager;
    private final TournamentConductHistory history;

    @Around("@annotation(change)")
    public Object recordChange(ProceedingJoinPoint invocation, ConductChange change) {
        return new TransactionTemplate(transactionManager).execute(status ->
                history.record(change, invocation.getArgs(), () -> proceed(invocation)));
    }

    private Object proceed(ProceedingJoinPoint invocation) {
        try {
            return invocation.proceed();
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IllegalStateException(exception);
        }
    }
}
