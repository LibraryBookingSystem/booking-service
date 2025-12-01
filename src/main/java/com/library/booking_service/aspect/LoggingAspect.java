package com.library.booking_service.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AOP Aspect for logging method execution
 */
@Aspect
@Component
public class LoggingAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);
    
    @Around("execution(* com.library.booking_service.service..*(..)) || " +
            "execution(* com.library.booking_service.controller..*(..))")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        
        logger.info("➡️  [{}] Calling method: {}", className, methodName);
        
        long startTime = System.currentTimeMillis();
        
        Object result = null;
        try {
            result = joinPoint.proceed();
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            logger.info("✅ [{}] Method {} completed in {}ms", 
                       className, methodName, executionTime);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            logger.error("❌ [{}] Method {} failed after {}ms: {}", 
                        className, methodName, executionTime, e.getMessage());
            
            throw e;
        }
        
        return result;
    }
}

