package com.sun.note.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(ErrorCode.INVALID_REQUEST.name());
        problem.setDetail(ErrorCode.INVALID_REQUEST.getMessage());

        log.warn("Validation 실패: {}", e.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(ErrorCode.INVALID_REQUEST.name());
        problem.setDetail(ErrorCode.INVALID_REQUEST.getMessage());

        log.warn("요청 본문 파싱 실패: {}", e.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(problem);
    }

    // 경로 변수/쿼리 파라미터 타입 불일치 (예: /api/chats/abc/messages)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException e) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(ErrorCode.INVALID_REQUEST.name());
        problem.setDetail(ErrorCode.INVALID_REQUEST.getMessage());

        log.warn("파라미터 타입 불일치: {}={}", e.getName(), e.getValue());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(problem);
    }

    // Content-Type 누락/미지원 (예: application/json 헤더 없이 body 전송)
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        problem.setTitle("UNSUPPORTED_MEDIA_TYPE");
        problem.setDetail(e.getMessage());

        log.warn("지원하지 않는 Content-Type: {}", e.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(problem);
    }

    // 매핑되지 않은 경로 (예: GET /api/chats/1). 없으면 아래 Exception 핸들러가 500으로 응답한다.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException e) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle(ErrorCode.RESOURCE_NOT_FOUND.name());
        problem.setDetail(ErrorCode.RESOURCE_NOT_FOUND.getMessage());

        log.warn("매핑되지 않은 경로: {}", e.getResourcePath());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(problem);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        problem.setTitle("METHOD_NOT_ALLOWED");
        problem.setDetail(e.getMessage());

        log.warn("지원하지 않는 HTTP 메서드: {}", e.getMessage());

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(problem);
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockException e) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle(ErrorCode.VERSION_CONFLICT.name());
        problem.setDetail(ErrorCode.VERSION_CONFLICT.getMessage());

        log.error("버전 충돌 발생 {}", e);

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(problem);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleJpaOptimisticLock(ObjectOptimisticLockingFailureException e) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle(ErrorCode.VERSION_CONFLICT.name());
        problem.setDetail(ErrorCode.VERSION_CONFLICT.getMessage());

        log.error("버전 충돌 발생 {}", e);

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(problem);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException e) {

        ErrorCode code = e.getErrorCode();

        ProblemDetail problem = ProblemDetail.forStatus(code.getStatus());
        problem.setTitle(code.name());
        problem.setDetail(code.getMessage());

        // 4xx는 정상적인 요청 거절이라 스택트레이스 없이 WARN, 5xx만 ERROR로 남긴다.
        if (code.getStatus().is5xxServerError()) {
            log.error("BusinessException 발생: {}", code, e);
        } else {
            log.warn("BusinessException 발생: {}", code);
        }

        return ResponseEntity
                .status(code.getStatus())
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception e) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        problem.setTitle("Internal Server Error");
        problem.setDetail("예상치 못한 오류가 발생했습니다.");

        log.error("예상치 못한 예외 발생", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem);
    }
}