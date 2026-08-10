package com.huarenzaimeng.api.buyerauth;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes=BuyerAuthController.class)
@Profile("release-mysql")
final class BuyerAuthHttpErrorHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class,HttpMediaTypeNotSupportedException.class})
    ResponseEntity<?> invalidHttpInput(){return BuyerAuthController.failure(400,"LOGIN_REQUEST_INVALID","NEW_CODE_REQUIRED",null);}
}
