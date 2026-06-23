/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.service.adl2;

import static org.assertj.core.api.Assertions.assertThat;

import com.nedap.archie.aom.OperationalTemplate;
import com.nedap.archie.query.RMObjectWithPath;
import com.nedap.archie.query.RMPathQuery;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.Observation;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import com.nedap.archie.rminfo.ArchieRMInfoLookup;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ehrbase.adl2.knowledge.Adl2ArchetypeFlattener;
import org.ehrbase.api.dto.AqlQueryRequest;
import org.ehrbase.api.exception.StateConflictException;
import org.ehrbase.api.service.Adl2TemplateService;
import org.ehrbase.api.service.AqlQueryService;
import org.ehrbase.api.service.TemplateService;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.QueryResultDto;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.query.ResultHolder;

final class Adl2IntegrationSupport {

    static final String PRESCRIPTION_TEMPLATE = "openEHR-EHR-COMPOSITION.annotations_rm_path.v1.0.0";
    static final String WITH_RULES_WRAPPER_TEMPLATE = "openEHR-EHR-COMPOSITION.with_rules_wrapper.v1.0.0";
    static final String SPECIALIZED_OBS_WRAPPER_TEMPLATE =
            "openEHR-EHR-COMPOSITION.specialized_observation_wrapper.v1.0.0";

    private Adl2IntegrationSupport() {}

    static OperationalTemplate uploadStandardFixtures(Adl2TemplateService adl2TemplateService) {
        createTemplate(adl2TemplateService, "/adl2-fixtures/sample-opt.adls");
        String withRulesResource = "/adl2-fixtures/openEHR-EHR-OBSERVATION.with_rules.v1.0.0.opt2.adls";
        createTemplate(adl2TemplateService, withRulesResource);
        OperationalTemplate withRulesOpt =
                Adl2ArchetypeFlattener.parseOperationalTemplate(load(withRulesResource));
        createTemplate(
                adl2TemplateService,
                "/adl2-fixtures/openEHR-EHR-OBSERVATION.specialized_template_observation.v1.0.0.opt2.adls");
        createTemplate(
                adl2TemplateService, "/adl2-fixtures/openEHR-EHR-COMPOSITION.with_rules_wrapper.v1.0.0.opt2.adls");
        createTemplate(
                adl2TemplateService,
                "/adl2-fixtures/openEHR-EHR-COMPOSITION.specialized_observation_wrapper.v1.0.0.opt2.adls");
        return withRulesOpt;
    }

    private static void createTemplate(Adl2TemplateService adl2TemplateService, String resourcePath) {
        try {
            adl2TemplateService.create(load(resourcePath));
        } catch (StateConflictException ignored) {
            // Shared Spring test context may already contain fixtures from another IT class.
        }
    }

    static Composition bloodPressureFromFlat(
            TemplateService templateService, double systolic, double diastolic, double pulsePressure) {
        Composition composition = templateService.buildExample(WITH_RULES_WRAPPER_TEMPLATE);
        Observation observation = (Observation) composition.getContent().get(0);
        setMagnitude(observation, "/data[id2]/events[id3]/data[id4]/items[id5]/value/magnitude", systolic);
        setMagnitude(observation, "/data[id2]/events[id3]/data[id4]/items[id6]/value/magnitude", diastolic);
        setMagnitude(observation, "/data[id2]/events[id3]/data[id4]/items[id7]/value/magnitude", pulsePressure);
        return composition;
    }

    static void setEndorsement(Composition composition, String endorsement) {
        DvText text = findFirst("/context/other_context/items[id3]/items[id5]/value", composition, DvText.class);
        text.setValue(endorsement);
    }

    static void setWeightKg(Composition composition, double weightKg) {
        DvQuantity quantity =
                findFirst("/context/other_context/items[id3]/items[id7]/value", composition, DvQuantity.class);
        quantity.setMagnitude(weightKg);
        quantity.setUnits("kg");
    }

    static QueryResultDto aql(AqlQueryService aqlQueryService, UUID ehrId, String queryFile) {
        return aqlQueryService.query(AqlQueryRequest.prepare(
                loadQuery(queryFile), Map.of("ehrId", ehrId.toString()), null, null));
    }

    static Object column(ResultHolder row, QueryResultDto result, String name) {
        int idx = new ArrayList<>(result.getVariables().keySet()).indexOf(name);
        return row.values().get(idx);
    }

    static String textColumn(ResultHolder row, QueryResultDto result, String name) {
        Object value = column(row, result, name);
        if (value instanceof DvText dvText) {
            return dvText.getValue();
        }
        return value != null ? value.toString() : null;
    }

    static ResultHolder singleRow(QueryResultDto result) {
        assertThat(result.getResultSet()).hasSize(1);
        return result.getResultSet().get(0);
    }

    static ResultHolder rowWithColumnValue(QueryResultDto result, String name, double expected) {
        return result.getResultSet().stream()
                .filter(row -> ((Number) column(row, result, name)).doubleValue() == expected)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No row with " + name + " = " + expected));
    }

    private static void setMagnitude(Observation observation, String magnitudePath, double magnitude) {
        String quantityPath = magnitudePath.substring(0, magnitudePath.lastIndexOf("/magnitude"));
        List<RMObjectWithPath> quantities =
                new RMPathQuery(quantityPath).findList(ArchieRMInfoLookup.getInstance(), observation);
        if (quantities.isEmpty()) {
            throw new IllegalStateException("No quantity at path " + quantityPath);
        }
        for (RMObjectWithPath match : quantities) {
            DvQuantity quantity = (DvQuantity) match.getObject();
            quantity.setMagnitude(magnitude);
            quantity.setUnits("mmHg");
            quantity.setPrecision(1L);
        }
    }

    private static <T> T findFirst(String path, Object root, Class<T> type) {
        List<RMObjectWithPath> values =
                new RMPathQuery(path).findList(ArchieRMInfoLookup.getInstance(), root);
        if (values.isEmpty() || !type.isInstance(values.get(0).getObject())) {
            throw new IllegalStateException("No " + type.getSimpleName() + " at path " + path);
        }
        return type.cast(values.get(0).getObject());
    }

    static String load(String path) {
        try (var in = Adl2IntegrationSupport.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String loadQuery(String fileName) {
        return load("/adl2-fixtures/queries/" + fileName);
    }
}
