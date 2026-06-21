/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.service.validation;

import com.nedap.archie.flattener.OperationalTemplateProvider;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rminfo.ArchieRMInfoLookup;
import com.nedap.archie.rmobjectvalidator.RMObjectValidationMessage;
import com.nedap.archie.rmobjectvalidator.RMObjectValidator;
import com.nedap.archie.rmobjectvalidator.ValidationConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.ehrbase.openehr.sdk.validation.ConstraintViolation;
import org.ehrbase.openehr.sdk.validation.terminology.ExternalTerminologyValidation;
import org.ehrbase.openehr.sdk.validation.webtemplate.FastRMObjectValidator;
import org.ehrbase.openehr.sdk.validation.webtemplate.ValidationWalker;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;

/**
 * Composition validator compatible with Archie 3.18+.
 *
 * <p>Uses {@link EhrbaseArchetypeNeglectingRMObjectValidator} instead of the SDK variant that
 * extends {@link FastRMObjectValidator} with a broken parent constructor chain.
 */
public class EhrbaseCompositionValidator {

    private final boolean checkForChildrenNotInTemplate;
    private final RMObjectValidator rmObjectValidator;
    private ExternalTerminologyValidation externalTerminologyValidation;

    public EhrbaseCompositionValidator(
            ExternalTerminologyValidation externalTerminologyValidation,
            boolean checkForChildrenNotInTemplate,
            boolean validateInvariants,
            OperationalTemplateProvider archetypeProvider) {
        this.externalTerminologyValidation = externalTerminologyValidation;
        this.checkForChildrenNotInTemplate = checkForChildrenNotInTemplate;

        ValidationConfiguration validationCfg = new ValidationConfiguration.Builder()
                .validateInvariants(validateInvariants)
                .failOnUnknownTerminologyId(false)
                .build();

        if (archetypeProvider != null) {
            rmObjectValidator = new FastRMObjectValidator(
                    ArchieRMInfoLookup.getInstance(), archetypeProvider, validationCfg);
        } else {
            rmObjectValidator = new EhrbaseArchetypeNeglectingRMObjectValidator(
                    ArchieRMInfoLookup.getInstance(), archetypeId -> null, validationCfg);
        }
    }

    public List<ConstraintViolation> validate(Composition composition, OPERATIONALTEMPLATE template) {
        return validate(composition, new OPTParser(template).parse());
    }

    public List<ConstraintViolation> validate(Composition composition, WebTemplate template) {
        List<RMObjectValidationMessage> messages = rmObjectValidator.validate(composition);
        if (messages.isEmpty()) {
            List<ConstraintViolation> result = new ArrayList<>();
            new ValidationWalker(externalTerminologyValidation, checkForChildrenNotInTemplate)
                    .walk(composition, result, template.getTree(), template.getTemplateId());
            return result;
        }
        return messages.stream()
                .map(validationMessage ->
                        new ConstraintViolation(validationMessage.getPath(), validationMessage.getMessage()))
                .collect(Collectors.toList());
    }

    public RMObjectValidator getRmObjectValidator() {
        return rmObjectValidator;
    }

    public void setExternalTerminologyValidation(ExternalTerminologyValidation externalTerminologyValidation) {
        this.externalTerminologyValidation = externalTerminologyValidation;
    }
}
