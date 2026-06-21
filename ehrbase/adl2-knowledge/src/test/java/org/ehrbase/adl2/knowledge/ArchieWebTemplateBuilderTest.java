/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.nedap.archie.adlparser.ADLParser;
import com.nedap.archie.aom.Archetype;
import com.nedap.archie.aom.CComplexObject;
import com.nedap.archie.aom.CObject;
import com.nedap.archie.aom.OperationalTemplate;
import com.nedap.archie.rm.composition.Composition;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplateNode;
import org.ehrbase.openehr.sdk.webtemplate.webtemplateskeletonbuilder.WebTemplateSkeletonBuilder;
import org.junit.jupiter.api.Test;

class ArchieWebTemplateBuilderTest {

    private final ArchieWebTemplateBuilder builder = new ArchieWebTemplateBuilder();

    @Test
    void sampleOpt_definitionHasAttributes() throws Exception {
        OperationalTemplate opt = parseOpt("/adl2-fixtures/sample-opt.adls");
        assertThat(opt.getDefinition().getAttributes()).isNotEmpty();
        CObject firstChild = opt.getDefinition().getAttributes().get(0).getChildren().get(0);
        assertThat(firstChild).isInstanceOf(CComplexObject.class);
        assertThat(firstChild.getRmTypeName()).isEqualTo("DV_CODED_TEXT");
    }

    @Test
    void sampleOpt_buildsWebTemplateWithRmAttributeChildren() throws Exception {
        OperationalTemplate opt = parseOpt("/adl2-fixtures/sample-opt.adls");
        WebTemplate webTemplate = builder.build(opt, "en");

        assertThat(webTemplate.getTemplateId()).isEqualTo("openEHR-EHR-COMPOSITION.annotations_rm_path.v1.0.0");
        WebTemplateNode root = webTemplate.getTree();
        assertThat(root.getRmType()).isEqualTo("COMPOSITION");
        assertThat(root.getChildren()).extracting(WebTemplateNode::getId).contains("composer");
    }

    @Test
    void sampleOpt_contentIncludesRequiredMedicationInstructionSlot() throws Exception {
        OperationalTemplate opt = parseOpt("/adl2-fixtures/sample-opt.adls");
        WebTemplate webTemplate = builder.build(opt, "en");

        WebTemplateNode medicationSlot = webTemplate.getTree().findChildById("medication_instruction").orElse(null);
        if (medicationSlot == null) {
            medicationSlot = WebTemplateNode.streamSubtree(webTemplate.getTree(), true)
                    .filter(n -> "id8".equals(n.getAqlPathDto().getLastNode().getAtCode()))
                    .findFirst()
                    .orElseThrow();
        }
        assertThat(medicationSlot.getMin()).isGreaterThanOrEqualTo(1);
        assertThat(medicationSlot.getAqlPathDto().getLastNode().getAtCode()).isEqualTo("id8");
        assertThat(medicationSlot.getNodeId()).isEqualTo("id8");
        assertThat(medicationSlot.isArchetypeSlot()).isFalse();
        assertThat(medicationSlot.getChildren()).isNotEmpty();
    }

    @Test
    void withRulesObservationOpt_buildsObservationRootWebTemplate() throws Exception {
        OperationalTemplate opt = parseOpt("/adl2-fixtures/openEHR-EHR-OBSERVATION.with_rules.v1.0.0.opt2.adls");
        WebTemplate webTemplate = builder.build(opt, "en");

        assertThat(webTemplate.getTree().getRmType()).isEqualTo("OBSERVATION");
        assertThat(webTemplate.getTree().getNodeId()).isEqualTo("openEHR-EHR-OBSERVATION.with_rules.v1.0.0");
    }

    @Test
    void withRulesWrapperOpt_expandsObservationSlotWithArchetypeNodeId() throws Exception {
        OperationalTemplate opt = parseOpt("/adl2-fixtures/openEHR-EHR-COMPOSITION.with_rules_wrapper.v1.0.0.opt2.adls");
        WebTemplate webTemplate = builder.build(opt, "en");

        WebTemplateNode observation = WebTemplateNode.streamSubtree(webTemplate.getTree(), true)
                .filter(n -> "OBSERVATION".equals(n.getRmType()))
                .findFirst()
                .orElseThrow();
        assertThat(observation.getNodeId()).isEqualTo("openEHR-EHR-OBSERVATION.with_rules.v1.0.0");
        assertThat(observation.getAqlPathDto().getLastNode().getAtCode())
                .isEqualTo("openEHR-EHR-OBSERVATION.with_rules.v1.0.0");
        assertThat(observation.getMin()).isGreaterThanOrEqualTo(1);
        assertThat(observation.isArchetypeSlot()).isFalse();
        assertThat(observation.getChildren()).isNotEmpty();
    }

    @Test
    void specializedObservationWrapperOpt_expandsObservationSlotWithArchetypeNodeId() throws Exception {
        OperationalTemplate opt =
                parseOpt("/adl2-fixtures/openEHR-EHR-COMPOSITION.specialized_observation_wrapper.v1.0.0.opt2.adls");
        WebTemplate webTemplate = builder.build(opt, "en");

        WebTemplateNode observation = WebTemplateNode.streamSubtree(webTemplate.getTree(), true)
                .filter(n -> "OBSERVATION".equals(n.getRmType()))
                .findFirst()
                .orElseThrow();
        assertThat(observation.getNodeId())
                .isEqualTo("openEHR-EHR-OBSERVATION.specialized_template_observation.v1.0.0");
        assertThat(observation.getAqlPathDto().getLastNode().getAtCode())
                .isEqualTo("openEHR-EHR-OBSERVATION.specialized_template_observation.v1.0.0");
        assertThat(observation.getMin()).isGreaterThanOrEqualTo(1);
        assertThat(observation.getChildren()).isNotEmpty();
    }

    @Test
    void sampleOpt_rootUsesFullArchetypeIdForAqlMatching() throws Exception {
        OperationalTemplate opt = parseOpt("/adl2-fixtures/sample-opt.adls");
        WebTemplate webTemplate = builder.build(opt, "en");
        assertThat(webTemplate.getTree().getNodeId())
                .isEqualTo("openEHR-EHR-COMPOSITION.annotations_rm_path.v1.0.0");
    }

    @Test
    void sampleOpt_skeletonBuilderProducesComposition() throws Exception {
        OperationalTemplate opt = parseOpt("/adl2-fixtures/sample-opt.adls");
        WebTemplate webTemplate = builder.build(opt, "en");

        Composition composition = WebTemplateSkeletonBuilder.build(webTemplate, false);
        assertThat(composition).isNotNull();
        assertThat(composition.getArchetypeNodeId()).isNotBlank();
    }

    private static OperationalTemplate parseOpt(String resourcePath) throws Exception {
        try (InputStream in = ArchieWebTemplateBuilderTest.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing resource " + resourcePath);
            }
            String adl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Archetype parsed = new ADLParser().parse(adl);
            if (!(parsed instanceof OperationalTemplate operationalTemplate)) {
                throw new IllegalStateException("Expected operational template");
            }
            return operationalTemplate;
        }
    }
}
