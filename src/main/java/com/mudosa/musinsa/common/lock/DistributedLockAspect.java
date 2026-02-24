package com.mudosa.musinsa.common.lock;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Order(0)
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private static final String LOCK_PREFIX = "lock:stock:";

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    /* 단일 락 */
    @Around("@annotation(distributedLock)")
    public Object aroundSingleLock(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = parseKey(joinPoint, distributedLock.key());
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION);
            }

            log.info("분산락 획득: {}", lockKey);
            return joinPoint.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("분산락 해제: {}", lockKey);
            }
        }
    }

    /* 멀티 락 */
    @Around("@annotation(distributedMultiLock)")
    public Object aroundMultiLock(ProceedingJoinPoint joinPoint, DistributedMultiLock distributedMultiLock) throws Throwable {
        List<Long> resourceIds = parseKeyList(joinPoint, distributedMultiLock.keys());

        // 데드락 방지: ID 오름차순 정렬
        List<Long> sortedIds = resourceIds.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        List<RLock> acquiredLocks = new ArrayList<>();

        try {
            // 순서대로 락 획득
            for (Long id : sortedIds) {
                String lockKey = LOCK_PREFIX + id;
                RLock lock = redissonClient.getLock(lockKey);

                boolean acquired = lock.tryLock(
                        distributedMultiLock.waitTime(),
                        distributedMultiLock.leaseTime(),
                        TimeUnit.SECONDS
                );

                if (!acquired) {
                    throw new BusinessException(ErrorCode.LOCK_ACQUISITION);
                }

                acquiredLocks.add(lock);
                log.info("분산락 획득: {}", lockKey);
            }

            return joinPoint.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION);
        } finally {
            // 역순 해제 (LIFO)
            for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
                RLock lock = acquiredLocks.get(i);
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    log.info("분산락 해제");
                }
            }
        }
    }

    private String parseKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        EvaluationContext context = createEvaluationContext(joinPoint);
        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseKeyList(ProceedingJoinPoint joinPoint, String keyExpression) {
        EvaluationContext context = createEvaluationContext(joinPoint);
        return (List<Long>) parser.parseExpression(keyExpression).getValue(context, List.class);
    }

    private EvaluationContext createEvaluationContext(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        StandardEvaluationContext context = new StandardEvaluationContext();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = nameDiscoverer.getParameterNames(method);

        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        return context;
    }
}
