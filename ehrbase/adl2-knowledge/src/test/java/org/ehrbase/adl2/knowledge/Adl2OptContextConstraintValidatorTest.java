/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.nedap.archie.aom.CComplexObject;
import com.nedap.archie.aom.CObject;
import com.nedap.archie.aom.OperationalTemplate;
import com.nedap.archie.aom.primitives.CString;
import com.nedap.archie.aom.primitives.COrdered;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.EventContext;
import com.nedap.archie.rm.datastructures.Cluster;
import com.nedap.archie.rm.datastructures.Element;
import com.nedap.archie.rm.datastructures.ItemTree;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import com.nedap.archie.rmobjectvalidator.RMObjectValidationMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class Adl2OptContextConstraintValidatorTest {

    private final Adl2KnowledgeServiceImpl knowledgeService = new Adl2KnowledgeServiceImpl();

    @Test
    void sampleOpt_hasContextStringAndQuantityConstraints() throws Exception {
        OperationalTemplate opt = parseOpt("/adl2-fixtures/sample-opt.adls");
        assertThat(count(opt.getDefinition(), false)).isGreaterThan(0);
        assertThat(count(opt.getDefinition(), true)).isGreaterThan(0);
    }

    @Test
    void sampleOpt_jsonRoundTrip_preservesContextConstraints() throws Exception {
        OperationalTemplate opt = parseOpt("/adl2-fixtures/sample-opt.adls");
        OperationalTemplate fromJson = knowledgeService.deserializeOptJson(knowledgeService.serializeOptJson(opt));
        assertThat(count(fromJson.getDefinition(), false)).isGreaterThan(0);
        assertThat(count(fromJson.getDefinition(), true)).isGreaterThan(0);
    }

    @Test
    void sampleOpt_rejectsInvalidEndorsement() throws Exception {
        OperationalTemplate opt = parseOpt("/adl2-fixtures/sample-opt.adls");
        Composition composition = prescriptionContextComposition("NotAllowedName", 80.0);

        List<RMObjectValidationMessage> messages =
                Adl2OptContextConstraintValidator.validate(composition, opt);
        assertThat(messages).isNotEmpty();
    }

    @Test
    void sampleOpt_rejectsOutOfRangeWeight() throws Exception {
        OperationalTemplate opt = parseOpt("/adl2-fixtures/sample-opt.adls");
        Composition composition = prescriptionContextComposition("Robert", 450.0);

        List<RMObjectValidationMessage> messages =
                Adl2OptContextConstraintValidator.validate(composition, opt);
        assertThat(messages).isNotEmpty();
    }

    @Test
    void sampleOpt_deserializedJson_rejectsInvalidEndorsement() throws Exception {
        OperationalTemplate opt = parseOpt("/adl2-fixtures/sample-opt.adls");
        OperationalTemplate fromJson = knowledgeService.deserializeOptJson(knowledgeService.serializeOptJson(opt));
        Composition composition = prescriptionContextComposition("NotAllowedName", 80.0);

        List<RMObjectValidationMessage> messages =
                Adl2OptContextConstraintValidator.validate(composition, fromJson);
        assertThat(messages).isNotEmpty();
    }

    private static Composition prescriptionContextComposition(String endorsement, double weightKg) {
        DvText endorsementValue = new DvText(endorsement);
        Element endorsementElement = new Element("id5", new DvText("Endorsement"), endorsementValue);

        DvQuantity weightValue = new DvQuantity("kg", weightKg, null);
        Element weightElement = new Element("id7", new DvText("Comment"), weightValue);

        Cluster qualification = new Cluster("id3", new DvText("Qualification"), List.of(endorsementElement, weightElement));
        ItemTree otherContext = new ItemTree("id2", new DvText("Tree"), List.of(qualification));
        EventContext context = new EventContext();
        context.setOtherContext(otherContext);

        Composition composition = new Composition();
        composition.setContext(context);
        return composition;
    }

    private static int count(CObject object, boolean quantity) {
        int total = 0;
        if (quantity && object instanceof COrdered<?> cOrdered && !cOrdered.getConstraint().isEmpty()) {
            total++;
        }
        if (!quantity && object instanceof CString cString && !cString.getConstraint().isEmpty()) {
            total++;
        }
        if (object instanceof CComplexObject complexObject && complexObject.getAttributes() != null) {
            for (var attribute : complexObject.getAttributes()) {
                if (attribute.getChildren() == null) {
                    continue;
                }
                for (CObject child : attribute.getChildren()) {
                    total += count(child, quantity);
                }
            }
        }
        return total;
    }

    private static OperationalTemplate parseOpt(String resourcePath) throws Exception {
        return Adl2ArchetypeFlattener.parseOperationalTemplate(read(resourcePath));
    }

    private static String read(String path) throws Exception {
        try (var in = Adl2OptContextConstraintValidatorTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes());
        }
    }
}
