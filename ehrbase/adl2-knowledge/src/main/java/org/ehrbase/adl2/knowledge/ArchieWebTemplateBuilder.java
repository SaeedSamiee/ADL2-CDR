/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

import com.nedap.archie.ArchieLanguageConfiguration;
import com.nedap.archie.aom.OperationalTemplate;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.parser.ArchieOptParser;

/**
 * Builds openEHR SDK {@link WebTemplate} trees from Archie ADL2 {@link OperationalTemplate} constraints.
 */
public class ArchieWebTemplateBuilder {

    public WebTemplate build(OperationalTemplate opt, String language) {
        ArchieLanguageConfiguration.setThreadLocalDescriptiongAndMeaningLanguage(language);
        try {
            return new ArchieOptParser(opt, language).parse();
        } finally {
            ArchieLanguageConfiguration.setThreadLocalDescriptiongAndMeaningLanguage(null);
        }
    }
}
