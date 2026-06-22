/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.service.adl2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
import org.ehrbase.adl2.knowledge.Adl2CompositionRuleValidator;
import org.ehrbase.api.dto.AqlQueryRequest;
import org.ehrbase.api.service.Adl2TemplateService;
import org.ehrbase.api.service.AqlQueryService;
import org.ehrbase.api.service.EhrService;
import org.ehrbase.api.service.TemplateService;
import org.ehrbase.api.service.ValidationService;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.QueryResultDto;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.query.ResultHolder;
import org.ehrbase.openehr.sdk.validation.ConstraintViolationException;
import org.ehrbase.service.CompositionServiceImp;
import org.ehrbase.test.Adl2DataIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * PostgreSQL integration tests for Archie 2 OPT fixtures:
 * <ul>
 *   <li>{@code sample-opt.adls} – prescription composition with medication instruction archetype slot</li>
 *   <li>{@code with_rules.v1.adls} – blood pressure observation with ADL rules (flattened to OPT at upload)</li>
 *   <li>{@code specialized_template_observation.opt2.adls} – observation with referenced cluster overlay archetype</li>
 * </ul>
 *
 * <p>Constraint violations and ADL rule assertions are enforced on commit via WebTemplate validation,
 * Archie OPT validation, and {@link Adl2CompositionRuleValidator} for nested component archetypes.
 */
@Adl2DataIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Adl2CompositionDataIntegrationIT {

    private static final String PRESCRIPTION_TEMPLATE = "openEHR-EHR-COMPOSITION.annotations_rm_path.v1.0.0";
    private static final String WITH_RULES_TEMPLATE = "openEHR-EHR-OBSERVATION.with_rules.v1.0.0";
    private static final String WITH_RULES_WRAPPER_TEMPLATE = "openEHR-EHR-COMPOSITION.with_rules_wrapper.v1.0.0";
    private static final String SPECIALIZED_OBS_TEMPLATE =
            "openEHR-EHR-OBSERVATION.specialized_template_observation.v1.0.0";
    private static final String SPECIALIZED_OBS_WRAPPER_TEMPLATE =
            "openEHR-EHR-COMPOSITION.specialized_observation_wrapper.v1.0.0";

    @Autowired
    Adl2TemplateService adl2TemplateService;

    @Autowired
    TemplateService templateService;

    @Autowired
    CompositionServiceImp compositionService;

    @Autowired
    ValidationService validationService;

    @Autowired
    EhrService ehrService;

    @Autowired
    AqlQueryService aqlQueryService;

    private OperationalTemplate withRulesOpt;
    private UUID ehrId;

    @BeforeAll
    void uploadFixtures() {
        adl2TemplateService.create(load("/adl2-fixtures/sample-opt.adls"));
        String withRulesOptAdl = load("/adl2-fixtures/openEHR-EHR-OBSERVATION.with_rules.v1.0.0.opt2.adls");
        withRulesOpt = Adl2ArchetypeFlattener.parseOperationalTemplate(withRulesOptAdl);
        adl2TemplateService.create(withRulesOptAdl);
        adl2TemplateService.create(
                load("/adl2-fixtures/openEHR-EHR-OBSERVATION.specialized_template_observation.v1.0.0.opt2.adls"));
        adl2TemplateService.create(load("/adl2-fixtures/openEHR-EHR-COMPOSITION.with_rules_wrapper.v1.0.0.opt2.adls"));
        adl2TemplateService.create(
                load("/adl2-fixtures/openEHR-EHR-COMPOSITION.specialized_observation_wrapper.v1.0.0.opt2.adls"));
    }

    @BeforeEach
    void setUp() {
        ehrId = ehrService.create(null, null).ehrId();
    }

    @Test
    void prescriptionTemplate_exampleComposition_persistsAndIsQueryable() {
        Composition composition = templateService.buildExample(PRESCRIPTION_TEMPLATE);
        setEndorsement(composition, "Robert");
        setWeightKg(composition, 80.0);
        assertDoesNotThrow(() -> validationService.check(composition));
        compositionService.create(ehrId, composition);

        QueryResultDto result = aql("prescription_qualification.aql");
        ResultHolder row = singleRow(result);
        assertThat(column(row, result, "composition_uid")).isNotNull();
    }

    @Test
    void prescriptionTemplate_nonCompliantFlatEndorsement_rejectedAtValidation() {
        Composition composition = templateService.buildExample(PRESCRIPTION_TEMPLATE);
        setEndorsement(composition, "NotAllowedName");

        assertThatThrownBy(() -> validationService.check(composition))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void prescriptionTemplate_nonCompliantFlatWeight_rejectedAtValidation() {
        Composition composition = templateService.buildExample(PRESCRIPTION_TEMPLATE);
        setWeightKg(composition, 450.0);

        assertThatThrownBy(() -> validationService.check(composition))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void bloodPressureRules_compliantFlatData_passesRulesAndPersistsForAql() {
        Composition composition = bloodPressureFromFlat(120.0, 80.0, 40.0);
        assertThat(Adl2ArchetypeFlattener.assertionPassed(
                        Adl2ArchetypeFlattener.evaluateRules(
                                (com.nedap.archie.rm.archetyped.Pathable) composition.getContent().get(0), withRulesOpt),
                        "systolic_greater_than_diastolic"))
                .isTrue();
        assertDoesNotThrow(() -> validationService.check(composition));
        compositionService.create(ehrId, composition);

        QueryResultDto result = aql("blood_pressure_with_rules.aql");
        ResultHolder row = rowWithColumnValue(result, "systolic", 120.0);
        assertThat(((Number) column(row, result, "systolic")).doubleValue()).isEqualTo(120.0);
        assertThat(((Number) column(row, result, "diastolic")).doubleValue()).isEqualTo(80.0);
        assertThat(((Number) column(row, result, "pulse_pressure")).doubleValue()).isEqualTo(40.0);
    }

    @Test
    void bloodPressureRules_nonCompliantAssertion_rejectedAtValidation() {
        Composition composition = bloodPressureFromFlat(75.0, 95.0, 20.0);
        assertThat(Adl2ArchetypeFlattener.assertionPassed(
                        Adl2ArchetypeFlattener.evaluateRules(
                                (com.nedap.archie.rm.archetyped.Pathable) composition.getContent().get(0), withRulesOpt),
                        "systolic_greater_than_diastolic"))
                .isFalse();
        assertThatThrownBy(() -> validationService.check(composition))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void bloodPressureRules_nonCompliantMagnitudeRange_rejectedAtValidation() {
        Composition composition = bloodPressureFromFlat(1500.0, 80.0, 1420.0);
        assertThatThrownBy(() -> validationService.check(composition))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void specializedObservationTemplate_exampleComposition_persistsAndIsQueryable() {
        Composition composition = templateService.buildExample(SPECIALIZED_OBS_WRAPPER_TEMPLATE);
        assertDoesNotThrow(() -> validationService.check(composition));
        compositionService.create(ehrId, composition);

        QueryResultDto result = aql("specialized_observation.aql");
        ResultHolder row = singleRow(result);
        assertThat(column(row, result, "observation_text")).isNotNull();
    }

    private Composition bloodPressureFromFlat(double systolic, double diastolic, double pulsePressure) {
        Composition composition = templateService.buildExample(WITH_RULES_WRAPPER_TEMPLATE);
        Observation observation = (Observation) composition.getContent().get(0);
        setMagnitude(observation, "/data[id2]/events[id3]/data[id4]/items[id5]/value/magnitude", systolic);
        setMagnitude(observation, "/data[id2]/events[id3]/data[id4]/items[id6]/value/magnitude", diastolic);
        setMagnitude(observation, "/data[id2]/events[id3]/data[id4]/items[id7]/value/magnitude", pulsePressure);
        return composition;
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

    private static void setEndorsement(Composition composition, String endorsement) {
        DvText text = findFirst("/context/other_context/items[id3]/items[id5]/value", composition, DvText.class);
        text.setValue(endorsement);
    }

    private static void setWeightKg(Composition composition, double weightKg) {
        DvQuantity quantity =
                findFirst("/context/other_context/items[id3]/items[id7]/value", composition, DvQuantity.class);
        quantity.setMagnitude(weightKg);
        quantity.setUnits("kg");
    }

    private static <T> T findFirst(String path, Object root, Class<T> type) {
        List<RMObjectWithPath> values =
                new RMPathQuery(path).findList(ArchieRMInfoLookup.getInstance(), root);
        if (values.isEmpty() || !type.isInstance(values.get(0).getObject())) {
            throw new IllegalStateException("No " + type.getSimpleName() + " at path " + path);
        }
        return type.cast(values.get(0).getObject());
    }

    private QueryResultDto aql(String queryFile) {
        return aqlQueryService.query(
                AqlQueryRequest.prepare(loadQuery(queryFile), Map.of("ehrId", ehrId.toString()), null, null));
    }

    private static Object column(ResultHolder row, QueryResultDto result, String name) {
        int idx = new ArrayList<>(result.getVariables().keySet()).indexOf(name);
        return row.values().get(idx);
    }

    private static ResultHolder rowWithColumnValue(QueryResultDto result, String name, double expected) {
        return result.getResultSet().stream()
                .filter(row -> ((Number) column(row, result, name)).doubleValue() == expected)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No row with " + name + " = " + expected));
    }

    private static ResultHolder singleRow(QueryResultDto result) {
        assertThat(result.getResultSet()).hasSize(1);
        return result.getResultSet().get(0);
    }

    private static String load(String path) {
        try (java.io.InputStream in = Adl2CompositionDataIntegrationIT.class.getResourceAsStream(path)) {
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
