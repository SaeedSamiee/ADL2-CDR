/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.service.adl2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.nedap.archie.rm.composition.Composition;
import java.util.UUID;
import org.ehrbase.api.service.AqlQueryService;
import org.ehrbase.api.service.Adl2TemplateService;
import org.ehrbase.api.service.EhrService;
import org.ehrbase.api.service.TemplateService;
import org.ehrbase.api.service.ValidationService;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.QueryResultDto;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.query.ResultHolder;
import org.ehrbase.service.CompositionServiceImp;
import org.ehrbase.test.Adl2DataIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * PostgreSQL integration tests focused on AQL retrieval of ADL2 template compositions.
 */
@Adl2DataIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Adl2AqlIntegrationIT {

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

    private UUID ehrId;

    @BeforeAll
    void uploadFixtures() {
        Adl2IntegrationSupport.uploadStandardFixtures(adl2TemplateService);
    }

    @BeforeEach
    void setUp() {
        ehrId = ehrService.create(null, null).ehrId();
    }

    @Test
    void prescriptionContext_endorsementAndWeight_areQueryable() {
        Composition composition = templateService.buildExample(Adl2IntegrationSupport.PRESCRIPTION_TEMPLATE);
        Adl2IntegrationSupport.setEndorsement(composition, "Clara");
        Adl2IntegrationSupport.setWeightKg(composition, 72.5);
        assertDoesNotThrow(() -> validationService.check(composition));
        compositionService.create(ehrId, composition);

        QueryResultDto result = query("prescription_qualification.aql");
        ResultHolder row = Adl2IntegrationSupport.singleRow(result);
        assertThat(Adl2IntegrationSupport.column(row, result, "composition_uid")).isNotNull();
        assertThat(Adl2IntegrationSupport.textColumn(row, result, "endorsement")).isEqualTo("Clara");
        assertThat(((Number) Adl2IntegrationSupport.column(row, result, "weight_kg")).doubleValue())
                .isEqualTo(72.5);
    }

    @Test
    void prescriptionTemplate_filterByArchetypeId_returnsComposition() {
        Composition composition = templateService.buildExample(Adl2IntegrationSupport.PRESCRIPTION_TEMPLATE);
        Adl2IntegrationSupport.setEndorsement(composition, "Rick");
        Adl2IntegrationSupport.setWeightKg(composition, 90.0);
        assertDoesNotThrow(() -> validationService.check(composition));
        compositionService.create(ehrId, composition);

        QueryResultDto result = query("prescription_by_template.aql");
        ResultHolder row = Adl2IntegrationSupport.singleRow(result);
        assertThat(Adl2IntegrationSupport.column(row, result, "composition_uid")).isNotNull();
    }

    @Test
    void bloodPressureObservation_returnsAllVitalSignColumns() {
        Composition composition = Adl2IntegrationSupport.bloodPressureFromFlat(templateService, 118.0, 76.0, 42.0);
        assertDoesNotThrow(() -> validationService.check(composition));
        compositionService.create(ehrId, composition);

        QueryResultDto result = query("blood_pressure_with_rules.aql");
        ResultHolder row = Adl2IntegrationSupport.rowWithColumnValue(result, "systolic", 118.0);
        assertThat(((Number) Adl2IntegrationSupport.column(row, result, "diastolic")).doubleValue())
                .isEqualTo(76.0);
        assertThat(((Number) Adl2IntegrationSupport.column(row, result, "pulse_pressure")).doubleValue())
                .isEqualTo(42.0);
    }

    @Test
    void specializedObservation_returnsTextAndClusterOverlay() {
        Composition composition = templateService.buildExample(Adl2IntegrationSupport.SPECIALIZED_OBS_WRAPPER_TEMPLATE);
        assertDoesNotThrow(() -> validationService.check(composition));
        compositionService.create(ehrId, composition);

        QueryResultDto result = query("specialized_observation.aql");
        ResultHolder row = Adl2IntegrationSupport.singleRow(result);
        assertThat(Adl2IntegrationSupport.textColumn(row, result, "observation_text")).isNotBlank();
        assertThat(result.getVariables()).containsKey("cluster_overlay_text");
    }

    @Test
    void emptyEhr_returnsZeroCompositions() {
        QueryResultDto result = query("compositions_count.aql");
        ResultHolder row = Adl2IntegrationSupport.singleRow(result);
        assertThat(((Number) Adl2IntegrationSupport.column(row, result, "composition_count")).longValue())
                .isZero();
    }

    @Test
    void persistedCompositions_increaseCount() {
        Composition prescription = templateService.buildExample(Adl2IntegrationSupport.PRESCRIPTION_TEMPLATE);
        Adl2IntegrationSupport.setEndorsement(prescription, "Robert");
        Adl2IntegrationSupport.setWeightKg(prescription, 80.0);
        assertDoesNotThrow(() -> validationService.check(prescription));
        compositionService.create(ehrId, prescription);

        Composition bloodPressure = Adl2IntegrationSupport.bloodPressureFromFlat(templateService, 120.0, 80.0, 40.0);
        assertDoesNotThrow(() -> validationService.check(bloodPressure));
        compositionService.create(ehrId, bloodPressure);

        QueryResultDto result = query("compositions_count.aql");
        ResultHolder row = Adl2IntegrationSupport.singleRow(result);
        assertThat(((Number) Adl2IntegrationSupport.column(row, result, "composition_count")).longValue())
                .isEqualTo(2L);
    }

    @Test
    void crossEhrIsolation_dataNotVisibleInOtherEhr() {
        Composition composition = templateService.buildExample(Adl2IntegrationSupport.PRESCRIPTION_TEMPLATE);
        Adl2IntegrationSupport.setEndorsement(composition, "Robert");
        Adl2IntegrationSupport.setWeightKg(composition, 80.0);
        assertDoesNotThrow(() -> validationService.check(composition));
        compositionService.create(ehrId, composition);

        UUID otherEhrId = ehrService.create(null, null).ehrId();
        QueryResultDto result = Adl2IntegrationSupport.aql(aqlQueryService, otherEhrId, "prescription_qualification.aql");
        assertThat(result.getResultSet()).isEmpty();
    }

    private QueryResultDto query(String queryFile) {
        return Adl2IntegrationSupport.aql(aqlQueryService, ehrId, queryFile);
    }
}
