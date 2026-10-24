package com.skycast.weather.web;

import com.skycast.weather.repository.FetchAuditLogRepository;
import com.skycast.weather.service.FetchAuditService;
import com.skycast.weather.service.LocationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/weather/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    public record JobRun(Long executionId, String status, String exitCode, long read, long written, long skipped) {
    }

    private final JobLauncher jobLauncher;
    private final Job historyImportJob;
    private final LocationService locations;
    private final FetchAuditService audit;
    private final FetchAuditLogRepository auditRepository;

    public AdminController(JobLauncher jobLauncher, Job historyImportJob, LocationService locations,
                           FetchAuditService audit, FetchAuditLogRepository auditRepository) {
        this.jobLauncher = jobLauncher;
        this.historyImportJob = historyImportJob;
        this.locations = locations;
        this.audit = audit;
        this.auditRepository = auditRepository;
    }

    /** Runs the Spring Batch history import synchronously and reports its step counters. */
    @PostMapping("/history-import")
    public JobRun importHistory(@RequestParam(defaultValue = "30") @Min(1) @Max(366) long days) throws Exception {
        JobExecution execution = jobLauncher.run(historyImportJob, new JobParametersBuilder()
                .addLong("days", days)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters());
        long read = 0, written = 0, skipped = 0;
        for (StepExecution step : execution.getStepExecutions()) {
            read += step.getReadCount();
            written += step.getWriteCount();
            skipped += step.getSkipCount();
        }
        return new JobRun(execution.getId(), execution.getStatus().name(), execution.getExitStatus().getExitCode(),
                read, written, skipped);
    }

    @PutMapping("/locations/{id}")
    public LocationView rename(@PathVariable Long id, @RequestParam @NotBlank String name) {
        return LocationView.of(locations.rename(id, name));
    }

    @GetMapping("/audit")
    public Map<String, Object> audit() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", audit.total());
        body.put("failures", audit.failures());
        Map<String, Long> byEndpoint = new LinkedHashMap<>();
        List<Object[]> rows = auditRepository.failuresByEndpoint();
        for (Object[] row : rows) {
            byEndpoint.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        body.put("failuresByEndpoint", byEndpoint);
        return body;
    }
}
