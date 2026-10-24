package com.skycast.alert;

import com.skycast.alert.service.AlertStreamService;
import com.skycast.common.dto.AlertDto;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;

/** Reactive test with StepVerifier: a subscriber only receives its own user's alerts. */
class AlertStreamServiceTest {

    private static AlertDto alert(long id) {
        return new AlertDto(id, "Dehradun", 30.32, 78.03, "TEST", "INFO", "hello", 0, 0, false, LocalDateTime.now());
    }

    @Test
    void deliversOnlyTheUsersAlerts() {
        AlertStreamService stream = new AlertStreamService();

        StepVerifier.create(stream.streamFor("aman").take(2))
                .then(() -> {
                    stream.publish("aman", alert(1));
                    stream.publish("someone-else", alert(2));
                    stream.publish("aman", alert(3));
                })
                .expectNextMatches(a -> a.id() == 1)
                .expectNextMatches(a -> a.id() == 3)
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }
}
