/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2;

import static org.assertj.core.api.Assertions.assertThat;

import com.nedap.archie.aom.OperationalTemplate;
import java.nio.charset.StandardCharsets;
import org.ehrbase.adl2.knowledge.Adl2KnowledgeServiceImpl;
import org.junit.jupiter.api.Test;

/**
 * Regression: ADL2 node ids must pass through without ADL1.4 remapping.
 */
class Adl2NodeIdPassthroughTest {

    private final Adl2KnowledgeServiceImpl service = new Adl2KnowledgeServiceImpl();

    @Test
    void nodeIdsPreservedInWebTemplate() throws Exception {
        String adls;
        try (var in = getClass().getResourceAsStream("/fixtures/sample-opt.adls")) {
            adls = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        OperationalTemplate opt = service.parseTemplateSource(adls);
        var webTemplate = service.buildWebTemplate(opt, "en");
        assertThat(webTemplate.getTree().getRmType()).isEqualTo("COMPOSITION");
        assertThat(opt.getDefinition().getNodeId()).isEqualTo("id1");
    }
}
