package com.skycast.weather.web;

import com.skycast.weather.domain.Location;
import com.skycast.weather.repository.LocationRepository;
import com.skycast.weather.repository.WeatherSnapshotRepository;
import com.skycast.weather.service.FetchAuditService;
import com.skycast.weather.service.LocationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Classic Spring MVC (not REST): the controller fills a Model and returns a view NAME; the
 * Thymeleaf view resolver maps "status" to templates/status.html. The search box is a bound,
 * validated form object.
 */
@Controller
@RequestMapping("/status")
public class StatusPageController {

    /** Form backing object (GET form, bound with @ModelAttribute and validated). */
    public record LocationSearchForm(@Size(max = 60) String name, @Size(max = 60) String country) {
    }

    private final LocationService locations;
    private final LocationRepository locationRepository;
    private final WeatherSnapshotRepository snapshots;
    private final FetchAuditService audit;

    public StatusPageController(LocationService locations, LocationRepository locationRepository,
                                WeatherSnapshotRepository snapshots, FetchAuditService audit) {
        this.locations = locations;
        this.locationRepository = locationRepository;
        this.snapshots = snapshots;
        this.audit = audit;
    }

    @GetMapping
    public String status(@Valid @ModelAttribute("form") LocationSearchForm form, BindingResult binding, Model model) {
        List<Location> found = binding.hasErrors() ? List.of() : locations.search(form.name(), form.country(), null, null);
        model.addAttribute("locations", found.stream().map(LocationView::of).toList());
        model.addAttribute("locationCount", locationRepository.count());
        model.addAttribute("snapshotCount", snapshots.count());
        model.addAttribute("providerCalls", audit.total());
        model.addAttribute("providerFailures", audit.failures());
        return "status";
    }
}
