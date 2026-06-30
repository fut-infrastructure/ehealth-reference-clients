package dk.sundhed.ehealth.referenceclients.clinician.app;

import java.util.List;

/**
 * Static catalogue of {@code Condition} codes the create-episode form lets a clinician pick from.
 *
 * <p>Codes are picked from the {@code http://ehealth.sundhed.dk/vs/conditions} value set bound to
 * the {@code Condition.code} element on the {@code ehealth-condition} profile. The codes are SKS
 * (Sundhedsvæsenets Klassifikations System, system {@code urn:oid:1.2.208.176.2.4}).
 */
public record ConditionCodeOption(String code, String display) {

    public static final String SYSTEM = "urn:oid:1.2.208.176.2.4";

    public static final List<ConditionCodeOption> OPTIONS = List.of(
            new ConditionCodeOption("DE11", "Type 2-diabetes"),
            new ConditionCodeOption("DJ44", "Kronisk obstruktiv lungesygdom"),
            new ConditionCodeOption("DI50", "Hjertesvigt"),
            new ConditionCodeOption("DI25", "Kronisk iskæmisk hjertesygdom"),
            new ConditionCodeOption("DG20", "Parkinsons sygdom"));

    /**
     * Returns the option whose {@link #code()} matches {@code code}, or {@code null} if none.
     */
    public static ConditionCodeOption byCode(String code) {
        return OPTIONS.stream()
                .filter(o -> o.code().equals(code))
                .findFirst()
                .orElse(null);
    }
}
