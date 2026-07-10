package dk.sundhed.ehealth.referenceclients.citizen.app;

import dk.sundhed.ehealth.referenceclients.citizen.api.ProcedureBundle;
import dk.sundhed.ehealth.referenceclients.citizen.api.ProcedureRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyActivitiesMapperTest {

    /**
     * A Monday.
     */
    private static final LocalDate WEEK_START = LocalDate.of(2026, 4, 13);

    private final WeeklyActivitiesMapper mapper = new WeeklyActivitiesMapper();

    @Test
    void scheduledRowLandsOnItsDayWithinTheWeek() {
        ProcedureRow row = resolved("ServiceRequest/1", "1", localDate(2026, 4, 15, 10, 0), 3, 1);

        WeekView week = mapper.map(WEEK_START, bundle(row));

        List<ActivityView> wednesday = week.days().get(2).activities();
        assertThat(wednesday).hasSize(1);
        ActivityView activity = wednesday.getFirst();
        assertThat(activity.timingType()).isEqualTo("Resolved");
        assertThat(activity.progress()).isEqualTo("1/3");
        assertThat(activity.carePlanId()).isEqualTo("9");
        assertThat(activity.episodeRef()).isEqualTo("EpisodeOfCare/5");
    }

    @Test
    void supersededServiceRequestVersionIsDropped() {
        Date slot = localDate(2026, 4, 15, 10, 0);
        ProcedureRow oldVersion = resolved("ServiceRequest/1", "1", slot, 3, 0);
        ProcedureRow newVersion = resolved("ServiceRequest/1", "2", slot, 3, 1);

        WeekView week = mapper.map(WEEK_START, bundle(oldVersion, newVersion));

        List<ActivityView> scheduled = allScheduled(week);
        assertThat(scheduled).hasSize(1);
        assertThat(scheduled.getFirst().serviceRequestVersionId()).isEqualTo("2");
    }

    @Test
    void unresolvedAndExtraRowsAreSkippedEntirely() {
        ProcedureRow unresolved = row("ServiceRequest/1", "1", "Unresolved", null, null, null);
        ProcedureRow extra = row("ServiceRequest/2", "1", "Extra",
                localDate(2026, 4, 15, 10, 0), null, null);

        WeekView week = mapper.map(WEEK_START, bundle(unresolved, extra));

        assertThat(allScheduled(week)).isEmpty();
        assertThat(week.unscheduled()).isEmpty();
    }

    @Test
    void adhocRowWithoutSlotGoesToUnscheduled() {
        ProcedureRow adhoc = row("ServiceRequest/1", "1", "Adhoc", null, null, null);

        WeekView week = mapper.map(WEEK_START, bundle(adhoc));

        assertThat(week.unscheduled()).hasSize(1);
        assertThat(week.unscheduled().getFirst().timingType()).isEqualTo("Adhoc");
    }

    @Test
    void resolvedRowOutsideTheWeekIsExcluded() {
        ProcedureRow nextWeek = resolved("ServiceRequest/1", "1", localDate(2026, 4, 21, 10, 0), 1, 0);

        WeekView week = mapper.map(WEEK_START, bundle(nextWeek));

        assertThat(allScheduled(week)).isEmpty();
    }

    @Test
    void progressIsNullWhenNoOccurrencesRequested() {
        ProcedureRow row = resolved("ServiceRequest/1", "1", localDate(2026, 4, 15, 10, 0), null, null);

        WeekView week = mapper.map(WEEK_START, bundle(row));

        assertThat(allScheduled(week).getFirst().progress()).isNull();
    }

    private static List<ActivityView> allScheduled(WeekView week) {
        return week.days().stream().flatMap(day -> day.activities().stream()).toList();
    }

    private static ProcedureBundle bundle(ProcedureRow... rows) {
        return new ProcedureBundle(List.of(rows), Map.of("9", "EpisodeOfCare/5"));
    }

    private static ProcedureRow resolved(
            String serviceRequestRef, String version, Date start, Integer requested, Integer submitted) {
        return row(serviceRequestRef, version, "Resolved", start, requested, submitted);
    }

    private static ProcedureRow row(
            String serviceRequestRef, String version, String timingType,
            Date start, Integer requested, Integer submitted) {
        return new ProcedureRow(
                "CarePlan/9", serviceRequestRef, version, "Puls",
                start, null, timingType, requested, submitted);
    }

    private static Date localDate(int year, int month, int day, int hour, int minute) {
        return Date.from(LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.systemDefault()).toInstant());
    }
}
