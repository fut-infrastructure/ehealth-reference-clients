package dk.sundhed.ehealth.referenceclients.citizen.app;

import dk.sundhed.ehealth.referenceclients.citizen.api.ProcedureRow;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a {@link WeekView} from a flat list of {@link ProcedureRow}s returned by
 * {@code $get-patient-procedures}.
 *
 * <p>{@code Resolved} / {@code Extra} rows with a {@code resolvedStart} falling inside the requested
 * Monday-anchored week are bucketed by date; {@code Unresolved} and {@code Adhoc} rows (no slot)
 * land in the {@code unscheduled} list, surfaced as a "Without time" section in the UI.
 */
@Component
public class WeeklyActivitiesMapper {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    public WeekView map(LocalDate weekStart, List<ProcedureRow> rows) {
        LocalDate monday = weekStart.with(DayOfWeek.MONDAY);
        LocalDate sundayExclusive = monday.plusDays(7);

        List<ActivityView> scheduledInWeek = new ArrayList<>();
        List<ActivityView> unscheduled = new ArrayList<>();

        for (ProcedureRow row : rows) {
            LocalDateTime resolvedAt = toLocal(row.resolvedStart());
            if (resolvedAt == null) {
                unscheduled.add(toView(row, null));
                continue;
            }
            LocalDate date = resolvedAt.toLocalDate();
            if (!date.isBefore(monday) && date.isBefore(sundayExclusive)) {
                scheduledInWeek.add(toView(row, resolvedAt));
            }
        }

        List<DayView> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            List<ActivityView> ofDay = new ArrayList<>();
            for (ActivityView a : scheduledInWeek) {
                if (a.scheduledAt() != null && a.scheduledAt().toLocalDate().equals(date)) {
                    ofDay.add(a);
                }
            }
            ofDay.sort(Comparator.comparing(ActivityView::scheduledAt));
            days.add(new DayView(date, List.copyOf(ofDay)));
        }
        return new WeekView(monday, List.copyOf(days), List.copyOf(unscheduled));
    }

    private static ActivityView toView(ProcedureRow row, LocalDateTime resolvedAt) {
        String title = row.activity() != null && !row.activity().isBlank()
                ? row.activity()
                : "Activity";
        return new ActivityView(
                title, resolvedAt, row.timingType(), progressOf(row), carePlanId(row.carePlanRef()));
    }

    /** Extracts the bare id from a CarePlan reference like {@code CarePlan/635491} or a full URL. */
    private static String carePlanId(String carePlanRef) {
        if (carePlanRef == null || carePlanRef.isBlank()) {
            return null;
        }
        int slash = carePlanRef.lastIndexOf('/');
        return slash >= 0 ? carePlanRef.substring(slash + 1) : carePlanRef;
    }

    private static String progressOf(ProcedureRow row) {
        if (row.occurrencesRequested() == null && row.totalSubmitted() == null) {
            return null;
        }
        int submitted = row.totalSubmitted() == null ? 0 : row.totalSubmitted();
        if (row.occurrencesRequested() == null) {
            return String.valueOf(submitted);
        }
        return submitted + "/" + row.occurrencesRequested();
    }

    private static LocalDateTime toLocal(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZONE);
    }
}
