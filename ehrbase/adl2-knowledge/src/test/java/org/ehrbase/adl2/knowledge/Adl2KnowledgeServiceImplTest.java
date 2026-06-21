/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.nedap.archie.aom.OperationalTemplate;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Adl2KnowledgeServiceImplTest {

    private final Adl2KnowledgeServiceImpl service = new Adl2KnowledgeServiceImpl();

    @Test
    void parseSerializeRoundTrip() throws Exception {
        String adls;
        try (var in = getClass().getResourceAsStream("/fixtures/sample-opt.adls")) {
            adls = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        OperationalTemplate opt = service.parseTemplateSource(adls);
        assertThat(opt.getArchetypeId().getFullId()).contains("COMPOSITION.annotations_rm_path");

        String json = service.serializeOptJson(opt);
        OperationalTemplate roundTrip = service.deserializeOptJson(json);
        assertThat(service.resolveTemplateId(roundTrip)).isEqualTo(service.resolveTemplateId(opt));

        assertThat(service.buildWebTemplate(roundTrip, "en").getTree()).isNotNull();
        assertThat(service.serializeAdl(roundTrip)).contains("operational_template");
    }
}
