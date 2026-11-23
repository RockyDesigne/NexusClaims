package com.titu.nexus_claims.services;

import java.util.*;

/** User-defined task names shared by automatic and manual task creation. */
public final class ClaimTaskCatalog {
    public static final Map<String, String> NAMES;
    static {
        var names = new LinkedHashMap<String, String>();
        names.put("DAMAGE_ASSESSMENT", "Damage Assessment");
        names.put("CLAIM_SETTLEMENT", "Claim Settlement");
        names.put("DAMAGE_REASSESSMENT", "Damage Reassessment");
        names.put("PAYMENT", "Payment");
        names.put("RECOVERY", "Recovery");
        names.put("ANTI_FRAUD", "Anti-fraud Investigation");
        NAMES = Collections.unmodifiableMap(names);
    }
    private ClaimTaskCatalog() {}
}
