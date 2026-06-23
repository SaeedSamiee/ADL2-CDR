/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.service.adl2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.nedap.archie.aom.OperationalTemplate;
import com.nedap.archie.rm.composition.Composition;
import java.util.UUID;
import org.ehrbase.adl2.knowledge.Adl2ArchetypeFlattener;
import org.ehrbase.adl2.knowledge.Adl2CompositionRuleValidator;
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
 * AQL-specific coverage lives in {@link Adl2AqlIntegrationIT}.
 */
@Adl2DataIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Adl2CompositionDataIntegrationIT {

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
        withRulesOpt = Adl2IntegrationSupport.uploadStandardFixtures(adl2TemplateService);
    }

    @BeforeEach
    void setUp() {
        ehrId = ehrService.create(null, null).ehrId();
    }

    @Test
    void prescriptionTemplate_exampleComposition_persistsAndIsQueryable() {
        Composition composition = templateService.buildExample(Adl2IntegrationSupport.PRESCRIPTION_TEMPLATE);
        Adl2IntegrationSupport.setEndorsement(composition, "Robert");
        Adl2IntegrationSupport.setWeightKg(composition, 80.0);
        assertDoesNotThrow(() -> validationService.check(composition));
        compositionService.create(ehrId, composition);

        QueryResultDto result = query("prescription_qualification.aql");
        ResultHolder row = Adl2IntegrationSupport.singleRow(result);
        assertThat(Adl2IntegrationSupport.column(row, result, "composition_uid")).isNotNull();
    }

    @Test
    void prescriptionTemplate_nonCompliantFlatEndorsement_rejectedAtValidation() {
        Composition composition = templateService.buildExample(Adl2IntegrationSupport.PRESCRIPTION_TEMPLATE);
        Adl2IntegrationSupport.setEndorsement(composition, "NotAllowedName");

        assertThatThrownBy(() -> validationService.check(composition))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void prescriptionTemplate_nonCompliantFlatWeight_rejectedAtValidation() {
        Composition composition = templateService.buildExample(Adl2IntegrationSupport.PRESCRIPTION_TEMPLATE);
        Adl2IntegrationSupport.setWeightKg(composition, 450.0);

        assertThatThrownBy(() -> validationService.check(composition))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void bloodPressureRules_compliantFlatData_passesRulesAndPersistsForAql() {
        Composition composition = Adl2IntegrationSupport.bloodPressureFromFlat(templateService, 120.0, 80.0, 40.0);
        assertThat(Adl2ArchetypeFlattener.assertionPassed(
                        Adl2ArchetypeFlattener.evaluateRules(
                                (com.nedap.archie.rm.archetyped.Pathable) composition.getContent().get(0), withRulesOpt),
                        "systolic_greater_than_diastolic"))
                .isTrue();
        assertDoesNotThrow(() -> validationService.check(composition));
        compositionService.create(ehrId, composition);

        QueryResultDto result = query("blood_pressure_with_rules.aql");
        ResultHolder row = Adl2IntegrationSupport.rowWithColumnValue(result, "systolic", 120.0);
        assertThat(((Number) Adl2IntegrationSupport.column(row, result, "systolic")).doubleValue()).isEqualTo(120.0);
        assertThat(((Number) Adl2IntegrationSupport.column(row, result, "diastolic")).doubleValue()).isEqualTo(80.0);
        assertThat(((Number) Adl2IntegrationSupport.column(row, result, "pulse_pressure")).doubleValue()).isEqualTo(40.0);
    }

    @Test
    void bloodPressureRules_nonCompliantAssertion_rejectedAtValidation() {
        Composition composition = Adl2IntegrationSupport.bloodPressureFromFlat(templateService, 75.0, 95.0, 20.0);
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
        Composition composition = Adl2IntegrationSupport.bloodPressureFromFlat(templateService, 1500.0, 80.0, 1420.0);
        assertThatThrownBy(() -> validationService.check(composition))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void specializedObservationTemplate_exampleComposition_persistsAndIsQueryable() {
        Composition composition = templateService.buildExample(Adl2IntegrationSupport.SPECIALIZED_OBS_WRAPPER_TEMPLATE);
        assertDoesNotThrow(() -> validationService.check(composition));
        compositionService.create(ehrId, composition);

        QueryResultDto result = query("specialized_observation.aql");
        ResultHolder row = Adl2IntegrationSupport.singleRow(result);
        assertThat(Adl2IntegrationSupport.column(row, result, "observation_text")).isNotNull();
    }

    private QueryResultDto query(String queryFile) {
        return Adl2IntegrationSupport.aql(aqlQueryService, ehrId, queryFile);
    }
}
