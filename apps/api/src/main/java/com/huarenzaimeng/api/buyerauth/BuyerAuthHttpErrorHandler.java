package com.huarenzaimeng.api.buyerauth;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(assignableTypes=BuyerAuthController.class)
@Profile("release-mysql")
final class BuyerAuthHttpErrorHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class,HttpMediaTypeNotSupportedException.class})
    ResponseEntity<?> invalidHttpInput(HttpServletRequest request){return request.getRequestURI().endsWith("/anonymous-sessions")
            ?BuyerAuthController.failure(400,"ANONYMOUS_SESSION_REQUEST_INVALID","NOT_RETRYABLE",null)
            :BuyerAuthController.failure(400,"LOGIN_REQUEST_INVALID","NEW_CODE_REQUIRED",null);}
}
