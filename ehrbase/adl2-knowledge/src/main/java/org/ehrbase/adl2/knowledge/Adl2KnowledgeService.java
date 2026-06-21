/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

import com.nedap.archie.aom.OperationalTemplate;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rmobjectvalidator.RMObjectValidationMessage;
import java.util.List;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;

/**
 * Archie ADL2 knowledge operations replacing openEHR SDK OPT 1.4 parsing for ADL2 templates.
 */
public interface Adl2KnowledgeService {

    OperationalTemplate parseTemplateSource(String source);

    String serializeOptJson(OperationalTemplate opt);

    OperationalTemplate deserializeOptJson(String json);

    String resolveTemplateId(OperationalTemplate opt);

    WebTemplate buildWebTemplate(OperationalTemplate opt, String language);

    List<RMObjectValidationMessage> validateComposition(Composition composition, OperationalTemplate opt);

    String serializeAdl(OperationalTemplate opt);
}
