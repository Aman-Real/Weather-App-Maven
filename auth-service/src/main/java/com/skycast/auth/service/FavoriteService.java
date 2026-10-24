package com.skycast.auth.service;

import com.skycast.auth.domain.AppUser;
import com.skycast.auth.domain.FavoriteLocation;
import com.skycast.auth.repository.FavoriteRepository;
import com.skycast.auth.repository.UserRepository;
import com.skycast.common.dto.FavoriteDto;
import com.skycast.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FavoriteService {

    private static final int MAX_FAVORITES = 20;

    private final UserRepository users;
    private final FavoriteRepository favorites;

    public FavoriteService(UserRepository users, FavoriteRepository favorites) {
        this.users = users;
        this.favorites = favorites;
    }

    @Transactional(readOnly = true)
    public List<FavoriteDto> list(String username) {
        return users.findWithFavorites(username)
                .map(u -> u.getFavorites().stream().map(FavoriteService::toDto).toList())
                .orElse(List.of());
    }

    @Transactional
    public FavoriteDto add(String username, FavoriteDto dto) {
        double lat = round(dto.latitude());
        double lon = round(dto.longitude());
        if (favorites.existsByUserUsernameAndLatitudeAndLongitude(username, lat, lon)) {
            throw new ApiException(HttpStatus.CONFLICT, dto.name() + " is already a favourite");
        }
        AppUser user = users.findWithFavorites(username)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getFavorites().size() >= MAX_FAVORITES) {
            throw ApiException.badRequest("You can save at most " + MAX_FAVORITES + " favourites");
        }
        FavoriteLocation favorite = new FavoriteLocation(dto.name(), dto.country(), lat, lon);
        user.addFavorite(favorite);
        favorites.saveAndFlush(favorite);
        return toDto(favorite);
    }

    @Transactional
    public void remove(String username, Long id) {
        FavoriteLocation favorite = favorites.findByIdAndUserUsername(id, username)
                .orElseThrow(() -> ApiException.notFound("Favourite not found"));
        favorite.getUser().removeFavorite(favorite);
        favorites.delete(favorite);
    }

    private static FavoriteDto toDto(FavoriteLocation f) {
        return new FavoriteDto(f.getId(), f.getName(), f.getCountry(), f.getLatitude(), f.getLongitude());
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
