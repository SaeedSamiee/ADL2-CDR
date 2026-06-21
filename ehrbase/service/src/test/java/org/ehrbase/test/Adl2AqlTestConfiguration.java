/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nedap.archie.rm.datavalues.DvCodedText;
import java.util.Collections;
import java.util.List;
import org.ehrbase.api.dto.AbstractAqlQueryContext;
import org.ehrbase.api.dto.AqlQueryContext;
import org.ehrbase.api.service.StatusService;
import org.ehrbase.openehr.sdk.util.functional.Try;
import org.ehrbase.openehr.sdk.validation.ConstraintViolation;
import org.ehrbase.openehr.sdk.validation.ConstraintViolationException;
import org.ehrbase.openehr.sdk.validation.terminology.ExternalTerminologyValidation;
import org.ehrbase.openehr.sdk.validation.terminology.TerminologyParam;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class Adl2AqlTestConfiguration {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    ExternalTerminologyValidation externalTerminologyValidation() {
        return new ExternalTerminologyValidation() {
            private final ConstraintViolation err = new ConstraintViolation("Terminology validation is disabled");

            @Override
            public Try<Boolean, ConstraintViolationException> validate(TerminologyParam param) {
                return Try.failure(new ConstraintViolationException(List.of(err)));
            }

            @Override
            public boolean supports(TerminologyParam param) {
                return false;
            }

            @Override
            public List<DvCodedText> expand(TerminologyParam param) {
                return Collections.emptyList();
            }
        };
    }

    @Bean
    AqlQueryContext aqlQueryContext() {
        return new AbstractAqlQueryContext(statusService(), true, true) {
            @Override
            protected boolean isGeneratorDetailsEnabled() {
                return false;
            }

            @Override
            public boolean showExecutedAql() {
                return false;
            }

            @Override
            public boolean isDryRun() {
                return false;
            }

            @Override
            public boolean showExecutedSql() {
                return false;
            }

            @Override
            public boolean showQueryPlan() {
                return false;
            }
        };
    }

    private static StatusService statusService() {
        return new StatusService() {
            @Override
            public String getOperatingSystemInformation() {
                return "";
            }

            @Override
            public String getJavaVMInformation() {
                return "";
            }

            @Override
            public String getDatabaseInformation() {
                return "";
            }

            @Override
            public String getEhrbaseVersion() {
                return "";
            }

            @Override
            public String getArchieVersion() {
                return "";
            }

            @Override
            public String getOpenEHR_SDK_Version() {
                return "";
            }
        };
    }
}
