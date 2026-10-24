package com.skycast.common.error;

import java.time.LocalDateTime;

public record ApiError(int status, String error, String message, String path, LocalDateTime timestamp) {
}
