/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.nedap.archie.aom.OperationalTemplate;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Adl2ArchetypeFlattenerTest {

    @Test
    void flattenWithRulesArchetypeToOpt() throws Exception {
        String archetypeAdl;
        try (var in = getClass().getResourceAsStream("/fixtures/openEHR-EHR-OBSERVATION.with_rules.v1.adls")) {
            archetypeAdl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        OperationalTemplate opt = Adl2ArchetypeFlattener.flattenArchetypeToOpt(archetypeAdl);
        assertThat(opt.getArchetypeId().getFullId()).contains("OBSERVATION.with_rules");
        assertThat(Adl2ArchetypeFlattener.serializeOptAdl(opt)).contains("operational_template");
    }
}
