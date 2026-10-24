package com.skycast.alert.service;

import com.skycast.common.dto.AlertDto;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-memory publish/subscribe hub built on a Reactor hot sink: every connected browser subscribes
 * to a Flux filtered to its own user. (Single instance; use a message broker for many replicas.)
 */
@Service
public class AlertStreamService {

    public record UserAlert(String username, AlertDto alert) {
    }

    private final Sinks.Many<UserAlert> sink = Sinks.many().multicast().directBestEffort();

    /** synchronized: a Sink must not be fed from two threads at once. */
    public synchronized void publish(String username, AlertDto alert) {
        sink.tryEmitNext(new UserAlert(username, alert));
    }

    public Flux<AlertDto> streamFor(String username) {
        return sink.asFlux()
                .filter(e -> e.username().equals(username))
                .map(UserAlert::alert);
    }

    public int subscribers() {
        return sink.currentSubscriberCount();
    }
}
