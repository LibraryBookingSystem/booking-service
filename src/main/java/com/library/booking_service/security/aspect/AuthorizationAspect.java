package com.library.booking_service.security.aspect;

import com.library.booking_service.entity.Booking;
import com.library.common.exception.ForbiddenException;
import com.library.booking_service.repository.BookingRepository;
import com.library.booking_service.security.annotation.RequiresBookingOwnership;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * AOP Aspect for booking-specific ownership checks.
 * Role-based authorization is handled by common-aspects BaseAuthorizationAspect.
 */
@Aspect
@Component
@Order(2) // Run after base authorization
public class AuthorizationAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationAspect.class);
    
    @Autowired
    private BookingRepository bookingRepository;
    
    @Before("@annotation(com.library.booking_service.security.annotation.RequiresBookingOwnership)")
    public void checkBookingOwnership(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequiresBookingOwnership annotation = method.getAnnotation(RequiresBookingOwnership.class);
        
        if (annotation == null) return;
        
        String userRole = getCurrentUserRole();
        Long userId = getCurrentUserId();
        
        if (userRole == null || userId == null) {
            throw new ForbiddenException("Authentication required");
        }
        
        if (annotation.adminBypass() && "ADMIN".equals(userRole)) {
            return;
        }
        
        Long bookingId = extractBookingId(joinPoint, method, annotation);
        if (bookingId == null) {
            throw new ForbiddenException("Booking ID not found");
        }
        
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ForbiddenException("Booking not found"));
        
        if (!userId.equals(booking.getUserId())) {
            throw new ForbiddenException("You do not have permission to access this booking");
        }
    }
    
    private Long extractBookingId(JoinPoint joinPoint, Method method, RequiresBookingOwnership annotation) {
        String paramName = annotation.bookingIdParam();
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();
        
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName) || parameters[i].getName().equals("id")) {
                Object arg = args[i];
                if (arg instanceof Long) {
                    return (Long) arg;
                } else if (arg instanceof String) {
                    try {
                        return Long.parseLong((String) arg);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
    
    private String getCurrentUserRole() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            return (String) request.getAttribute("userRole");
        }
        return null;
    }
    
    private Long getCurrentUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            Object userId = request.getAttribute("userId");
            if (userId instanceof Long) {
                return (Long) userId;
            } else if (userId instanceof Integer) {
                return ((Integer) userId).longValue();
            }
        }
        return null;
    }
}
