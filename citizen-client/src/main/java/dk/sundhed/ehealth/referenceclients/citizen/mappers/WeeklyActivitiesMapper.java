package dk.sundhed.ehealth.referenceclients.citizen.mappers;

import dk.sundhed.ehealth.referenceclients.citizen.models.ProcedureBundle;
import dk.sundhed.ehealth.referenceclients.citizen.models.ProcedureRow;
import dk.sundhed.ehealth.referenceclients.citizen.view.ActivityView;
import dk.sundhed.ehealth.referenceclients.citizen.view.DayView;
import dk.sundhed.ehealth.referenceclients.citizen.view.WeekView;
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

        // For a one-shot (non-repeating) ServiceRequest, the server can split one occurrence across
        // two rows for the same instant: a still-"due" row (OccurrencesRequested) and a submitted row
        // (TotalSubmitted). Merge counts per slot so the activity shows one progress figure instead
        // of a stale "still due" row alongside a "submitted" one.
        Map<SlotKey, ProgressCounts> progressBySlot = mergeProgressBySlot(rows, latestVersion);
        Set<SlotKey> emittedSlots = new HashSet<>();

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
                unscheduled.add(toView(row, null, episodes, progressOf(row)));
                continue;
            }
            SlotKey slot = new SlotKey(row.serviceRequestRef(), row.resolvedStart().toInstant());
            if (!emittedSlots.add(slot)) {
                continue;
            }
            LocalDate date = resolvedAt.toLocalDate();
            if (!date.isBefore(monday) && date.isBefore(sundayExclusive)) {
                scheduledInWeek.add(toView(row, resolvedAt, episodes, progressBySlot.get(slot)));
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
            ProcedureRow row, LocalDateTime resolvedAt, Map<String, String> episodes,
            ProgressCounts progress) {
        String title = row.activity() != null && !row.activity().isBlank()
                ? row.activity()
                : "Activity";
        String carePlanId = bareCarePlanId(row.carePlanRef());
        String episode = episodes.get(carePlanId);
        LocalDateTime resolvedEnd = toLocal(row.resolvedEnd());
        return new ActivityView(
                title, resolvedAt, resolvedEnd, row.timingType(), carePlanId,
                row.serviceRequestVersionId(), row.serviceRequestRef(), episode,
                progress.requested(), progress.submitted());
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

    /**
     * A ServiceRequest's resolved occurrence, identified by its instant rather than its formatted
     * offset so two equal instants written with different UTC offsets still match.
     */
    private record SlotKey(String serviceRequestRef, Instant resolvedStart) {
    }

    /**
     * How many measurements a slot expects vs. how many were submitted, either field possibly null
     * when no row for the slot reported it.
     */
    private record ProgressCounts(Integer requested, Integer submitted) {
        private static final ProgressCounts EMPTY = new ProgressCounts(null, null);

        private static ProgressCounts merge(ProgressCounts a, ProgressCounts b) {
            return new ProgressCounts(
                    a.requested != null ? a.requested : b.requested,
                    a.submitted != null ? a.submitted : b.submitted);
        }
    }

    /**
     * Merged {@link ProgressCounts} per resolved slot, combining a still-"due" row
     * (OccurrencesRequested) with a submitted row (TotalSubmitted) for the same instant when the
     * server split them across two rows instead of reporting both on one. Superseded versions are
     * excluded so a stale row's counts (e.g. a pre-submission {@code submitted: 0}) can't win over
     * the current version's just because zero isn't null.
     */
    private static Map<SlotKey, ProgressCounts> mergeProgressBySlot(
            List<ProcedureRow> rows,
            Map<String, Integer> latestVersion
    ) {
        Map<SlotKey, ProgressCounts> merged = new HashMap<>();

        for (ProcedureRow row : rows) {
            if (row.resolvedStart() == null || isSuperseded(row, latestVersion)) {
                continue;
            }
            SlotKey slot = new SlotKey(row.serviceRequestRef(), row.resolvedStart().toInstant());
            merged.merge(slot, progressOf(row), ProgressCounts::merge);
        }

        return merged;
    }

    private static ProgressCounts progressOf(ProcedureRow row) {
        if (row.occurrencesRequested() == null && row.totalSubmitted() == null) {
            return ProgressCounts.EMPTY;
        }
        return new ProgressCounts(row.occurrencesRequested(), row.totalSubmitted());
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

    private static LocalDateTime toLocal(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZONE);
    }
}
