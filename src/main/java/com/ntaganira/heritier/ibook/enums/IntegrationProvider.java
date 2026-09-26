/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.enums
 * - File      : IntegrationProvider.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : The outside services this application could be wired to
 * </pre>
 */
package com.ntaganira.heritier.ibook.enums;

/**
 * The outside services this application could be wired to, and what each one needs.
 *
 * <p><strong>None of them is wired.</strong> Nothing in this codebase opens a connection to any of
 * these: there is no HTTP client, no scheduled polling and no callback handler. What these pages do
 * is record the settings a connection would need, and say plainly what is still missing. That is
 * worth having — the settings are real, somebody has to gather them, and writing them down is the
 * first step of any integration — but it is not a connection, and the pages do not pretend it is.
 *
 * <p>Each provider declares which fields make sense for it, so a page asks for a merchant code only
 * where a merchant code exists and does not invite somebody to fill in a field that means nothing.
 */
public enum IntegrationProvider {

    EBM("ebm", true, true, true, true,
            "RRA certification under EBM 2.x, a registered SDU or virtual SDU, and the signing "
            + "credentials the RRA issues. None of that can be obtained from inside this "
            + "application, and an invoice is not fiscalised until it has been sent and signed."),

    MTN_MOMO("mtn-momo", true, true, true, true,
            "A MoMo merchant account, an API user and key from the MTN developer portal, and a "
            + "publicly reachable callback URL for MTN to notify. Collections also need the "
            + "subscriber to approve each request on their handset."),

    AIRTEL_MONEY("airtel-money", true, true, true, true,
            "An Airtel Money merchant account, client credentials from Airtel, and a publicly "
            + "reachable callback URL. As with MoMo, nothing can be collected without the "
            + "subscriber approving it."),

    BANK_FEED("banks", true, true, false, true,
            "An agreement with the bank and feed credentials. Until then statements are brought in "
            + "as files, which is what /banking/feeds does and is enough for reconciliation."),

    PAYMENT_GATEWAY("payments", true, true, true, true,
            "A gateway account, an API key, and a reachable callback URL for the gateway to confirm "
            + "a payment. Without it no payment link can be generated, because the link is issued "
            + "by the gateway and not by this application."),

    WEBHOOK("webhooks", true, false, false, false,
            "An outbound HTTP client, a queue for retries, and a signing secret so the receiver can "
            + "tell a genuine call from a forged one. None of the three exists here, so nothing is "
            + "delivered to a registered endpoint."),

    API("api", false, false, false, false,
            "There is no API surface. This application serves rendered pages and exposes no JSON "
            + "endpoints, so a key would grant access to nothing. Building one means deciding "
            + "authentication, versioning and what may be read or written — none of which is a "
            + "settings question.");

    private final String slug;
    private final boolean needsEndpoint;
    private final boolean needsParticipantId;
    private final boolean needsCallback;
    private final boolean hasEnvironments;
    private final String whatItWouldTake;

    IntegrationProvider(String slug, boolean needsEndpoint, boolean needsParticipantId,
                        boolean needsCallback, boolean hasEnvironments, String whatItWouldTake) {
        this.slug = slug;
        this.needsEndpoint = needsEndpoint;
        this.needsParticipantId = needsParticipantId;
        this.needsCallback = needsCallback;
        this.hasEnvironments = hasEnvironments;
        this.whatItWouldTake = whatItWouldTake;
    }

    public String slug() {
        return slug;
    }

    public boolean needsEndpoint() {
        return needsEndpoint;
    }

    public boolean needsParticipantId() {
        return needsParticipantId;
    }

    public boolean needsCallback() {
        return needsCallback;
    }

    public boolean hasEnvironments() {
        return hasEnvironments;
    }

    /** What is genuinely still missing, stated per provider rather than as one vague sentence. */
    public String whatItWouldTake() {
        return whatItWouldTake;
    }

    /** Whether more than one of these can usefully be recorded. Only webhooks can. */
    public boolean allowsMany() {
        return this == WEBHOOK;
    }

    /**
     * Whether there is anything to configure at all.
     *
     * <p>False for {@link #API}, and that is the point of the flag rather than an edge case to be
     * tidied away. There is no API surface in this application, so a page offering to record an API
     * key would be inviting somebody to configure access to something that does not exist. That page
     * says so and offers no form.
     */
    public boolean isConfigurable() {
        return this != API;
    }

    public static IntegrationProvider fromSlug(String slug) {
        if (slug == null) {
            return null;
        }
        for (IntegrationProvider provider : values()) {
            if (provider.slug.equalsIgnoreCase(slug)) {
                return provider;
            }
        }
        return null;
    }
}
