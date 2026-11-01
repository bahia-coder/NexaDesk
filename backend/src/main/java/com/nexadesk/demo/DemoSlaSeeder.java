package com.nexadesk.demo;

import com.nexadesk.sla.Holiday;
import com.nexadesk.sla.HolidayRepository;
import com.nexadesk.sla.SlaSettings;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Demo profile only: French public holidays for 2026 and 2027 (skipped if holidays already exist). */
@Slf4j
@Component
@Profile("demo")
@Order(3)
@RequiredArgsConstructor
public class DemoSlaSeeder implements ApplicationRunner {

    private static final Map<LocalDate, String> HOLIDAYS = new TreeMap<>(Map.ofEntries(
            Map.entry(LocalDate.of(2026, 1, 1), "Jour de l'an"),
            Map.entry(LocalDate.of(2026, 4, 6), "Lundi de Pâques"),
            Map.entry(LocalDate.of(2026, 5, 1), "Fête du Travail"),
            Map.entry(LocalDate.of(2026, 5, 8), "Victoire 1945"),
            Map.entry(LocalDate.of(2026, 5, 14), "Ascension"),
            Map.entry(LocalDate.of(2026, 5, 25), "Lundi de Pentecôte"),
            Map.entry(LocalDate.of(2026, 7, 14), "Fête nationale"),
            Map.entry(LocalDate.of(2026, 8, 15), "Assomption"),
            Map.entry(LocalDate.of(2026, 11, 1), "Toussaint"),
            Map.entry(LocalDate.of(2026, 11, 11), "Armistice 1918"),
            Map.entry(LocalDate.of(2026, 12, 25), "Noël"),
            Map.entry(LocalDate.of(2027, 1, 1), "Jour de l'an"),
            Map.entry(LocalDate.of(2027, 3, 29), "Lundi de Pâques"),
            Map.entry(LocalDate.of(2027, 5, 1), "Fête du Travail"),
            Map.entry(LocalDate.of(2027, 5, 6), "Ascension"),
            Map.entry(LocalDate.of(2027, 5, 8), "Victoire 1945"),
            Map.entry(LocalDate.of(2027, 5, 17), "Lundi de Pentecôte"),
            Map.entry(LocalDate.of(2027, 7, 14), "Fête nationale"),
            Map.entry(LocalDate.of(2027, 8, 15), "Assomption"),
            Map.entry(LocalDate.of(2027, 11, 1), "Toussaint"),
            Map.entry(LocalDate.of(2027, 11, 11), "Armistice 1918"),
            Map.entry(LocalDate.of(2027, 12, 25), "Noël")));

    private final HolidayRepository holidayRepository;
    private final SlaSettings settings;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (holidayRepository.count() > 0) {
            log.info("Holidays already present, skipping demo holidays");
            return;
        }
        HOLIDAYS.forEach((day, label) -> holidayRepository.save(new Holiday(day, label)));
        settings.invalidateAfterCommit();
        log.info("Demo holidays created: {}", HOLIDAYS.size());
    }
}
