package dk.sundhed.ehealth.referenceclients.citizen.app;

import java.time.LocalDate;
import java.util.List;

/**
 * One Monday-anchored week of the citizen's planned activities.
 *
 * @param weekStart   Monday of the week
 * @param days        exactly seven days, Monday through Sunday, in chronological order
 * @param unscheduled activities returned by {@code $get-patient-procedures} with TimingType
 *                    {@code Unresolved} or {@code Adhoc} (no resolved slot)
 */
public record WeekView(LocalDate weekStart, List<DayView> days, List<ActivityView> unscheduled) {

    public LocalDate previousWeek() {
        return weekStart.minusWeeks(1);
    }

    public LocalDate nextWeek() {
        return weekStart.plusWeeks(1);
    }
}
