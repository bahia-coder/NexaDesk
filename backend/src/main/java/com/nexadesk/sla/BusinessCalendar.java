package com.nexadesk.sla;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The single row (id = 1) holding the company's working hours. */
@Entity
@Table(name = "business_calendar")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessCalendar {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(name = "work_start", nullable = false)
    private LocalTime workStart;

    @Column(name = "work_end", nullable = false)
    private LocalTime workEnd;

    /** Comma-separated {@link DayOfWeek} names, e.g. "MONDAY,TUESDAY". */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "working_days", nullable = false, length = 70)
    private String workingDays;

    public Set<DayOfWeek> getWorkingDays() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        Arrays.stream(workingDays.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf).forEach(days::add);
        return days;
    }

    public void setWorkingDays(Collection<DayOfWeek> days) {
        this.workingDays = EnumSet.copyOf(days).stream().map(DayOfWeek::name).collect(Collectors.joining(","));
    }
}
