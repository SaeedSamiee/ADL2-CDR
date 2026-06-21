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
 * End-to-end ADL2 knowledge pipeline using Archie operational template fixtures
 * (mirrors dev-tenant upload → cache → web-template flow at the knowledge layer).
 */
class Adl2TemplatePipelineTest {

    private final Adl2KnowledgeServiceImpl service = new Adl2KnowledgeServiceImpl();

    @Test
    void uploadParseSerializeAndWebTemplate() throws Exception {
        String adls = loadResource("/fixtures/sample-opt.adls");
        OperationalTemplate opt = service.parseTemplateSource(adls);
        String templateId = service.resolveTemplateId(opt);
        assertThat(templateId).contains("COMPOSITION.annotations_rm_path");

        String storedJson = service.serializeOptJson(opt);
        OperationalTemplate cached = service.deserializeOptJson(storedJson);
        assertThat(service.resolveTemplateId(cached)).isEqualTo(templateId);

        var webTemplate = service.buildWebTemplate(cached, "en");
        assertThat(webTemplate.getTemplateId()).isEqualTo(templateId);
        assertThat(webTemplate.getTree().getChildren()).isNotEmpty();
        assertThat(webTemplate.findByAqlPath("id1")).isPresent();
    }

    @Test
    void adlSourceRoundTripPreservesNodeIds() throws Exception {
        String adls = loadResource("/fixtures/sample-opt.adls");
        OperationalTemplate opt = service.parseTemplateSource(adls);
        String reserializedAdl = service.serializeAdl(opt);
        OperationalTemplate reparsed = service.parseTemplateSource(reserializedAdl);
        assertThat(reparsed.getDefinition().getNodeId()).isEqualTo(opt.getDefinition().getNodeId());
    }

    private static String loadResource(String path) throws Exception {
        try (var in = Adl2TemplatePipelineTest.class.getResourceAsStream(path)) {
            assertThat(in).as("fixture %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
