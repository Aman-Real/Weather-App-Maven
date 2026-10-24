package com.skycast.auth.web;

import java.time.LocalDateTime;
import java.util.List;

public record UserProfile(Long id, String username, String email, List<String> roles, LocalDateTime createdAt) {
}
