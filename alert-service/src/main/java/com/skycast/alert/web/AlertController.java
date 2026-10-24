package com.skycast.alert.web;

import com.skycast.alert.service.AlertEngine;
import com.skycast.alert.service.AlertService;
import com.skycast.alert.service.AlertStreamService;
import com.skycast.common.dto.AlertDto;
import com.skycast.common.dto.AlertRuleDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alerts;
    private final AlertStreamService stream;
    private final AlertEngine engine;

    public AlertController(AlertService alerts, AlertStreamService stream, AlertEngine engine) {
        this.alerts = alerts;
        this.stream = stream;
        this.engine = engine;
    }

    @GetMapping
    public List<AlertDto> recent(@AuthenticationPrincipal Jwt jwt) {
        return alerts.recent(jwt.getSubject());
    }

    /**
     * Real-time push: a Flux of Server-Sent Events (Spring MVC streams reactive return types).
     * Emits "connected" once, an "alert" event per new alert, and a comment heartbeat every 25 s.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@AuthenticationPrincipal Jwt jwt) {
        String user = jwt.getSubject();
        Flux<ServerSentEvent<Object>> hello = Flux.just(ServerSentEvent.builder()
                .event("connected").data((Object) Map.of("user", user)).build());
        Flux<ServerSentEvent<Object>> live = stream.streamFor(user)
                .map(a -> ServerSentEvent.builder()
                        .event("alert").id(String.valueOf(a.id())).data((Object) a).build());
        Flux<ServerSentEvent<Object>> heartbeat = Flux.interval(Duration.ofSeconds(25))
                .map(i -> ServerSentEvent.builder().comment("keep-alive").build());
        return Flux.concat(hello, Flux.merge(live, heartbeat));
    }

    @GetMapping("/rules")
    public List<AlertRuleDto> rules(@AuthenticationPrincipal Jwt jwt) {
        return alerts.rules(jwt.getSubject());
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertRuleDto createRule(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AlertRuleDto rule) {
        return alerts.createRule(jwt.getSubject(), rule);
    }

    @PatchMapping("/rules/{id}")
    public AlertRuleDto toggleRule(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestParam boolean active) {
        return alerts.setActive(jwt.getSubject(), id, active);
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        alerts.deleteRule(jwt.getSubject(), id);
    }

    @PostMapping("/{id}/ack")
    public AlertDto acknowledge(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return alerts.acknowledge(jwt.getSubject(), id);
    }

    @PostMapping("/ack-all")
    public Map<String, Integer> acknowledgeAll(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("acknowledged", alerts.acknowledgeAll(jwt.getSubject()));
    }

    @PostMapping("/test")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertDto test(@AuthenticationPrincipal Jwt jwt) {
        return alerts.testAlert(jwt.getSubject());
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AlertDto> all() {
        return alerts.recentForAll();
    }

    @PostMapping("/admin/evaluate")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> evaluateNow() {
        return Map.of("raised", engine.evaluateNow(), "subscribers", stream.subscribers());
    }
}
