package com.skycast.auth.repository;

import com.skycast.auth.domain.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    /** Loads the LAZY favourites collection in the same query (avoids N+1 / LazyInitializationException). */
    @EntityGraph(attributePaths = "favorites")
    @Query("select u from AppUser u where u.username = :username")
    Optional<AppUser> findWithFavorites(@Param("username") String username);
}
