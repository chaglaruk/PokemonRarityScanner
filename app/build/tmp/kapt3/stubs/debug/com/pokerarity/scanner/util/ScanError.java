package com.pokerarity.scanner.util;

/**
 * Centralized error handling for the scan pipeline.
 *
 * Each [ScanError] carries a user-facing message and retry behavior.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u000f\b\u0086\u0081\u0002\u0018\u0000 \u00132\b\u0012\u0004\u0012\u00020\u00000\u0001:\u0001\u0013B\u0017\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0004\u0010\u0007R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tj\u0002\b\nj\u0002\b\u000bj\u0002\b\fj\u0002\b\rj\u0002\b\u000ej\u0002\b\u000fj\u0002\b\u0010j\u0002\b\u0011j\u0002\b\u0012\u00a8\u0006\u0014"}, d2 = {"Lcom/pokerarity/scanner/util/ScanError;", "", "userMessage", "", "isRetryable", "", "(Ljava/lang/String;ILjava/lang/String;Z)V", "()Z", "getUserMessage", "()Ljava/lang/String;", "CAPTURE_FAILED", "CAPTURE_TIMEOUT", "PERMISSION_DENIED", "OCR_FAILED", "OCR_INIT_FAILED", "LOW_CONFIDENCE_RESULT", "VISUAL_DETECTION_FAILED", "DATABASE_ERROR", "UNKNOWN", "Companion", "app_debug"})
public enum ScanError {
    /*public static final*/ CAPTURE_FAILED /* = new CAPTURE_FAILED(null, false) */,
    /*public static final*/ CAPTURE_TIMEOUT /* = new CAPTURE_TIMEOUT(null, false) */,
    /*public static final*/ PERMISSION_DENIED /* = new PERMISSION_DENIED(null, false) */,
    /*public static final*/ OCR_FAILED /* = new OCR_FAILED(null, false) */,
    /*public static final*/ OCR_INIT_FAILED /* = new OCR_INIT_FAILED(null, false) */,
    /*public static final*/ LOW_CONFIDENCE_RESULT /* = new LOW_CONFIDENCE_RESULT(null, false) */,
    /*public static final*/ VISUAL_DETECTION_FAILED /* = new VISUAL_DETECTION_FAILED(null, false) */,
    /*public static final*/ DATABASE_ERROR /* = new DATABASE_ERROR(null, false) */,
    /*public static final*/ UNKNOWN /* = new UNKNOWN(null, false) */;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String userMessage = null;
    private final boolean isRetryable = false;
    public static final int MAX_RETRIES = 2;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.util.ScanError.Companion Companion = null;
    
    ScanError(java.lang.String userMessage, boolean isRetryable) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getUserMessage() {
        return null;
    }
    
    public final boolean isRetryable() {
        return false;
    }
    
    @org.jetbrains.annotations.NotNull()
    public static kotlin.enums.EnumEntries<com.pokerarity.scanner.util.ScanError> getEntries() {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/pokerarity/scanner/util/ScanError$Companion;", "", "()V", "MAX_RETRIES", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}