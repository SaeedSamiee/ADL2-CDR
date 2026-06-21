/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.service;

import org.ehrbase.adl2.knowledge.Adl2KnowledgeService;
import org.ehrbase.adl2.knowledge.Adl2KnowledgeServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Adl2KnowledgeConfiguration {

    @Bean
    public Adl2KnowledgeService adl2KnowledgeService() {
        return new Adl2KnowledgeServiceImpl();
    }
}
