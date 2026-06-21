/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.ehrbase.ServiceModuleConfiguration;
import org.ehrbase.openehr.aqlengine.AqlEngineModuleConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Service-layer integration tests that include the AQL engine for querying persisted compositions.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {ServiceModuleConfiguration.class, AqlEngineModuleConfiguration.class, ServiceTestConfiguration.class, Adl2AqlTestConfiguration.class},
        properties = {
            "spring.main.banner-mode=off",
            "spring.main.log-startup-info=false",
            "spring.main.lazy-initialization=true",
            "ehrbase.aql.pg-llj-workaround=true",
        })
@ActiveProfiles("test")
public @interface Adl2DataIntegrationTest {}
