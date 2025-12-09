package com.mudosa.musinsa.common.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedMultiLock {
    String keys();  // SpEL - List<Long> 반환 표현식
    long waitTime() default 5;
    long leaseTime() default 3;
}