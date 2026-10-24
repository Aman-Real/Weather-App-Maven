package com.skycast.common.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AuthResponse(String token, String username, List<String> roles, LocalDateTime expiresAt) {
}
