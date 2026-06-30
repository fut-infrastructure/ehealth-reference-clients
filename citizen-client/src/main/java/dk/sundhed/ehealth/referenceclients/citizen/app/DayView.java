package dk.sundhed.ehealth.referenceclients.citizen.app;

import java.time.LocalDate;
import java.util.List;

/**
 * One calendar day in the citizen weekly view. Activities are pre-sorted by time-of-day ascending.
 * Days with no scheduled activities still appear so the week always renders seven rows.
 *
 * @param date       the calendar date
 * @param activities activities scheduled on this date, sorted ascending by time-of-day
 */
public record DayView(LocalDate date, List<ActivityView> activities) {
}
