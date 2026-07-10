package dk.sundhed.ehealth.referenceclients.citizen.app;

import dk.sundhed.ehealth.referenceclients.citizen.api.ProcedureBundle;
import dk.sundhed.ehealth.referenceclients.citizen.api.ProcedureRow;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;

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

    public WeekView map(LocalDate weekStart, ProcedureBundle bundle) {
        List<ProcedureRow> rows = bundle.rows();
        Map<String, String> episodes = bundle.episodesByCarePlanId();

        // The server returns a row per (ServiceRequest version, timing type). Superseded versions
        // linger, e.g. an old Adhoc bucket of past submissions alongside the current Resolved slot.
        // Keep only each ServiceRequest's newest version so stale buckets don't surface as activities.
        Map<String, Integer> latestVersion = latestVersions(rows);

        LocalDate monday = weekStart.with(DayOfWeek.MONDAY);
        LocalDate sundayExclusive = monday.plusDays(7);

        List<ActivityView> scheduledInWeek = new ArrayList<>();
        List<ActivityView> unscheduled = new ArrayList<>();

        for (ProcedureRow row : rows) {
            if (isSuperseded(row, latestVersion)) {
                continue;
            }
            // Unresolved = server couldn't compute a slot (config issue, not citizen-actionable).
            // Extra = measurement already submitted outside a slot; not a pending activity.
            String timingType = row.timingType();
            if ("Unresolved".equals(timingType) || "Extra".equals(timingType)) {
                continue;
            }
            LocalDateTime resolvedAt = toLocal(row.resolvedStart());
            if (resolvedAt == null) {
                unscheduled.add(toView(row, null, episodes));
                continue;
            }
            LocalDate date = resolvedAt.toLocalDate();
            if (!date.isBefore(monday) && date.isBefore(sundayExclusive)) {
                scheduledInWeek.add(toView(row, resolvedAt, episodes));
            }
        }

        List<DayView> days = new ArrayList<>(7);
        for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
            LocalDate date = monday.plusDays(dayIndex);
            List<ActivityView> ofDay = new ArrayList<>();
            for (ActivityView activity : scheduledInWeek) {
                if (activity.scheduledAt() != null && activity.scheduledAt().toLocalDate().equals(date)) {
                    ofDay.add(activity);
                }
            }
            ofDay.sort(Comparator.comparing(ActivityView::scheduledAt));
            days.add(new DayView(date, List.copyOf(ofDay)));
        }
        return new WeekView(monday, List.copyOf(days), List.copyOf(unscheduled));
    }

    private static ActivityView toView(
            ProcedureRow row, LocalDateTime resolvedAt, Map<String, String> episodes) {
        String title = row.activity() != null && !row.activity().isBlank()
                ? row.activity()
                : "Activity";
        String carePlanId = bareCarePlanId(row.carePlanRef());
        String episode = episodes.get(carePlanId);
        LocalDateTime resolvedEnd = toLocal(row.resolvedEnd());
        return new ActivityView(
                title, resolvedAt, resolvedEnd, row.timingType(), progressOf(row), carePlanId,
                row.serviceRequestVersionId(), row.serviceRequestRef(), episode);
    }

    /**
     * Bare logical id of a CarePlan reference (e.g. {@code CarePlan/635491} or a full URL).
     */
    private static String bareCarePlanId(String carePlanRef) {
        if (carePlanRef == null || carePlanRef.isBlank()) {
            return null;
        }
        return new IdType(carePlanRef).getIdPart();
    }

    /**
     * For each ServiceRequest reference, the highest numeric version present in the response.
     */
    private static Map<String, Integer> latestVersions(List<ProcedureRow> rows) {
        Map<String, Integer> latest = new HashMap<>();
        for (ProcedureRow row : rows) {
            String serviceRequestRef = row.serviceRequestRef();
            Integer version = versionOf(row);
            if (serviceRequestRef == null || version == null) {
                continue;
            }
            latest.merge(serviceRequestRef, version, Math::max);
        }
        return latest;
    }

    /**
     * True when the row belongs to an older version of its ServiceRequest than the newest seen.
     */
    private static boolean isSuperseded(ProcedureRow row, Map<String, Integer> latestVersion) {
        Integer newest = latestVersion.get(row.serviceRequestRef());
        Integer version = versionOf(row);
        return newest != null && version != null && version < newest;
    }

    private static Integer versionOf(ProcedureRow row) {
        String raw = row.serviceRequestVersionId();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException numberFormatException) {
            return null;
        }
    }

    private static String progressOf(ProcedureRow row) {
        if (row.occurrencesRequested() == null) {
            return null;
        }
        int submitted = row.totalSubmitted() == null ? 0 : row.totalSubmitted();
        return submitted + "/" + row.occurrencesRequested();
    }

    private static LocalDateTime toLocal(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZONE);
    }
}
