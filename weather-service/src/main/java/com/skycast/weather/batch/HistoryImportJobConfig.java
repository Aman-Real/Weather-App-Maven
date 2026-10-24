package com.skycast.weather.batch;

import com.skycast.weather.client.OpenMeteoClient;
import com.skycast.weather.client.OpenMeteoModels;
import com.skycast.weather.domain.DailyWeather;
import com.skycast.weather.domain.Location;
import com.skycast.weather.repository.DailyWeatherRepository;
import com.skycast.weather.repository.LocationRepository;
import com.skycast.weather.service.HistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Batch chunk job: for every stored location (reader) fetch the last N days from the
 * Open-Meteo archive (processor) and upsert them into daily_weather (writer), 5 locations per transaction.
 * Started from POST /api/weather/admin/history-import?days=30 (ADMIN).
 */
@Configuration
public class HistoryImportJobConfig {

    private static final Logger log = LoggerFactory.getLogger(HistoryImportJobConfig.class);

    @Bean
    public Job historyImportJob(JobRepository jobRepository, Step importDailyWeatherStep) {
        return new JobBuilder("historyImportJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(importDailyWeatherStep)
                .build();
    }

    @Bean
    public Step importDailyWeatherStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                       ListItemReader<Location> locationReader,
                                       ItemProcessor<Location, List<DailyWeather>> archiveProcessor,
                                       ItemWriter<List<DailyWeather>> dailyWeatherWriter) {
        return new StepBuilder("importDailyWeather", jobRepository)
                .<Location, List<DailyWeather>>chunk(5, transactionManager)
                .reader(locationReader)
                .processor(archiveProcessor)
                .writer(dailyWeatherWriter)
                .faultTolerant()
                .skip(RuntimeException.class)
                .skipLimit(20)
                .build();
    }

    @Bean
    @StepScope
    public ListItemReader<Location> locationReader(LocationRepository locations) {
        return new ListItemReader<>(locations.findAll());
    }

    @Bean
    @StepScope
    public ItemProcessor<Location, List<DailyWeather>> archiveProcessor(
            OpenMeteoClient client, DailyWeatherRepository daily, HistoryService history,
            @Value("#{jobParameters['days'] ?: 30}") Long days) {
        return location -> {
            LocalDate end = LocalDate.now().minusDays(1);
            LocalDate start = end.minusDays(days - 1);
            OpenMeteoModels.Forecast archive = client.archive(location.getLatitude(), location.getLongitude(), start, end);
            if (archive == null || archive.daily() == null) {
                return null; // null = filter this item out
            }
            List<DailyWeather> existing = daily.findRange(location.getId(), start, end);
            log.info("History import: {} ({} days)", location.getName(), days);
            return history.merge(location, archive.daily(), existing, start, end);
        };
    }

    @Bean
    public ItemWriter<List<DailyWeather>> dailyWeatherWriter(DailyWeatherRepository daily) {
        return chunk -> {
            for (List<DailyWeather> rows : chunk) {
                daily.saveAll(rows);
            }
        };
    }
}
