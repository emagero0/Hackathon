package com.erp.aierpbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Centralized system error handling service that provides retry logic
 * and error classification for network and connectivity issues.
 */
@Service
@Slf4j
public class SystemErrorHandler {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(2);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

    /**
     * Determines if an error is a system error that should be retried automatically.
     */
    public boolean isSystemError(Throwable error) {
        return error instanceof ConnectException ||
               error instanceof SocketTimeoutException ||
               error instanceof TimeoutException ||
               error instanceof IOException ||
               (error instanceof WebClientResponseException && isRetryableHttpStatus((WebClientResponseException) error));
    }

    /**
     * Determines if an error is a business logic error that should be shown to users.
     */
    public boolean isBusinessLogicError(Throwable error) {
        return !isSystemError(error) && !isCriticalSystemError(error);
    }

    /**
     * Determines if an error is critical and should be shown to users.
     */
    public boolean isCriticalSystemError(Throwable error) {
        // Authentication errors, authorization errors, etc.
        return error instanceof WebClientResponseException &&
               (((WebClientResponseException) error).getStatusCode().value() == 401 ||
                ((WebClientResponseException) error).getStatusCode().value() == 403);
    }

    /**
     * Creates a retry specification for system errors.
     */
    public Retry createSystemErrorRetry(String operationName) {
        return Retry.backoff(MAX_RETRY_ATTEMPTS, INITIAL_RETRY_DELAY)
                .maxBackoff(MAX_RETRY_DELAY)
                .filter(this::isSystemError)
                .doBeforeRetry(retrySignal -> {
                    log.warn("Retrying {} operation (attempt {}/{}) due to system error: {}",
                            operationName,
                            retrySignal.totalRetries() + 1,
                            MAX_RETRY_ATTEMPTS,
                            retrySignal.failure().getMessage());
                })
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                    log.error("All retry attempts exhausted for {} operation. Last error: {}",
                            operationName, retrySignal.failure().getMessage());
                    return new SystemErrorException("System temporarily unavailable for " + operationName +
                            " after " + MAX_RETRY_ATTEMPTS + " attempts", retrySignal.failure());
                });
    }

    /**
     * Wraps a Mono operation with system error retry logic.
     */
    public <T> Mono<T> withSystemErrorRetry(Mono<T> operation, String operationName) {
        return operation
                .retryWhen(createSystemErrorRetry(operationName))
                .onErrorMap(error -> {
                    if (isSystemError(error)) {
                        return new SystemErrorException("System error during " + operationName, error);
                    }
                    return error;
                });
    }

    /**
     * Creates a user-friendly error message for business logic errors.
     */
    public String createBusinessErrorMessage(String context, String specificError) {
        return String.format("%s: %s", context, specificError);
    }

    /**
     * Creates a user-friendly error message for missing identifiers.
     */
    public String createMissingIdentifierMessage(String documentType, String identifierType) {
        switch (documentType.toLowerCase()) {
            case "salesquote":
                if ("sales_quote_number".equals(identifierType)) {
                    return "Cannot find Sales Quote Number from Sales Quote document";
                }
                break;
            case "proformainvoice":
                if ("tax_invoice_number".equals(identifierType)) {
                    return "Cannot find Tax Invoice Number from Proforma Invoice document - please check Proforma Invoice";
                }
                break;
            case "jobconsumption":
                if ("job_number".equals(identifierType)) {
                    return "Cannot find Job Number from Job Consumption document";
                }
                break;
        }
        return String.format("Cannot find required identifier %s from %s document", identifierType, documentType);
    }

    private boolean isRetryableHttpStatus(WebClientResponseException error) {
        int status = error.getStatusCode().value();
        // Retry on server errors (5xx) and some client errors (408, 429)
        return status >= 500 || status == 408 || status == 429;
    }

    /**
     * Custom exception for system errors that should not be shown to users.
     */
    public static class SystemErrorException extends RuntimeException {
        public SystemErrorException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Custom exception for business logic errors that should be shown to users.
     */
    public static class BusinessLogicErrorException extends RuntimeException {
        public BusinessLogicErrorException(String message) {
            super(message);
        }

        public BusinessLogicErrorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
