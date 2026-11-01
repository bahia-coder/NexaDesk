package com.nexadesk.sla;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findAllByOrderByDayAsc();

    boolean existsByDay(LocalDate day);
}
