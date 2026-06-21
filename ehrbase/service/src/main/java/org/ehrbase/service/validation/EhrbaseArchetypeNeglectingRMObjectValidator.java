/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.service.validation;

import com.nedap.archie.aom.CObject;
import com.nedap.archie.flattener.OperationalTemplateProvider;
import com.nedap.archie.rminfo.ModelInfoLookup;
import com.nedap.archie.rmobjectvalidator.RMObjectValidationMessage;
import com.nedap.archie.rmobjectvalidator.RMObjectValidationMessageType;
import com.nedap.archie.rmobjectvalidator.RMObjectValidator;
import com.nedap.archie.rmobjectvalidator.ValidationConfiguration;
import java.util.Collection;

/**
 * Archie-compatible RM validator that ignores missing archetype OPTs.
 *
 * <p>The openEHR SDK {@code ArchetypeNeglectingRMObjectValidator} extends {@code FastRMObjectValidator},
 * which calls {@code RMObjectValidator(null, null, config)} and fails on Archie 3.18+.
 */
public class EhrbaseArchetypeNeglectingRMObjectValidator extends RMObjectValidator {

    public EhrbaseArchetypeNeglectingRMObjectValidator(
            ModelInfoLookup lookup,
            OperationalTemplateProvider provider,
            ValidationConfiguration validationConfiguration) {
        super(lookup, provider, validationConfiguration);
    }

    @Override
    protected void addMessage(RMObjectValidationMessage message) {
        if (message.getType() != RMObjectValidationMessageType.ARCHETYPE_NOT_FOUND) {
            super.addMessage(message);
        }
    }

    @Override
    protected void addMessage(
            CObject cobject, String actualPath, String message, RMObjectValidationMessageType type) {
        if (type != RMObjectValidationMessageType.ARCHETYPE_NOT_FOUND) {
            super.addMessage(cobject, actualPath, message, type);
        }
    }

    @Override
    protected void addAllMessages(Collection<RMObjectValidationMessage> messages) {
        messages.forEach(this::addMessage);
    }
}
