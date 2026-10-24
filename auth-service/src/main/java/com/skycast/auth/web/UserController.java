package com.skycast.auth.web;

import com.skycast.auth.domain.AppUser;
import com.skycast.auth.repository.UserRepository;
import com.skycast.auth.service.FavoriteService;
import com.skycast.common.dto.FavoriteDto;
import com.skycast.common.error.ApiException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final FavoriteService favoriteService;

    public UserController(UserRepository users, FavoriteService favoriteService) {
        this.users = users;
        this.favoriteService = favoriteService;
    }

    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal Jwt jwt) {
        return users.findByUsername(jwt.getSubject())
                .map(UserController::toProfile)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    @GetMapping("/me/favorites")
    public List<FavoriteDto> favorites(@AuthenticationPrincipal Jwt jwt) {
        return favoriteService.list(jwt.getSubject());
    }

    @PostMapping("/me/favorites")
    @ResponseStatus(HttpStatus.CREATED)
    public FavoriteDto addFavorite(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FavoriteDto favorite) {
        return favoriteService.add(jwt.getSubject(), favorite);
    }

    @DeleteMapping("/me/favorites/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        favoriteService.remove(jwt.getSubject(), id);
    }

    /** Method-level security: only ADMIN tokens may list every user. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserProfile> all(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        Page<AppUser> result = users.findAll(PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending()));
        return result.map(UserController::toProfile).getContent();
    }

    private static UserProfile toProfile(AppUser u) {
        return new UserProfile(u.getId(), u.getUsername(), u.getEmail(),
                u.getRoles().stream().map(Enum::name).sorted().toList(), u.getCreatedAt());
    }
}
