package com.eventcart.common.exception;

import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return badRequest(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return badRequest("요청 본문을 읽을 수 없습니다.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return badRequest(e.getName() + ": 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        ErrorCode code = ErrorCode.MISSING_MEMBER_HEADER;
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, code.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // 404(NoResourceFound), 405 등 Spring MVC 표준 예외는 원래 상태 코드를 유지한다
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            return ResponseEntity.status(springError.getStatusCode())
                    .body(new ErrorResponse("HTTP_" + springError.getStatusCode().value(), e.getMessage()));
        }
        log.error("Unexpected error", e);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }

    private ResponseEntity<ErrorResponse> badRequest(String message) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT, message));
    }
}
