/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

import com.nedap.archie.aom.OperationalTemplate;
import com.nedap.archie.rm.archetyped.Locatable;
import com.nedap.archie.rm.archetyped.Pathable;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rmobjectvalidator.RMObjectValidationMessage;
import com.nedap.archie.rmobjectvalidator.RMObjectValidationMessageType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Validates ADL2 OPT constraints on compositions, including nested component archetypes. */
public final class Adl2CompositionOptValidator {

    private Adl2CompositionOptValidator() {}

    public static List<RMObjectValidationMessage> validate(
            Composition composition,
            OperationalTemplate templateOpt,
            Function<String, Optional<OperationalTemplate>> componentOptLookup,
            BiFunction<OperationalTemplate, Pathable, List<RMObjectValidationMessage>> pathableValidator) {
        List<RMObjectValidationMessage> messages = new ArrayList<>();
        Composition normalized = Adl2CompositionCommitNormalizer.normalize(composition, templateOpt);
        boolean wrapperContent = hasWrapperContent(normalized);

        if (normalized.getContent() != null) {
            for (var item : normalized.getContent()) {
                if (item instanceof Locatable locatable) {
                    String archetypeNodeId = locatable.getArchetypeNodeId();
                    if (archetypeNodeId != null) {
                        componentOptLookup.apply(archetypeNodeId).ifPresent(opt -> messages.addAll(pathableValidator.apply(opt, locatable)));
                    }
                }
            }
        }

        if (!wrapperContent) {
            pathableValidator.apply(templateOpt, normalized).stream()
                    .filter(Adl2CompositionOptValidator::isTemplateConstraintViolation)
                    .forEach(messages::add);
        }
        messages.addAll(Adl2OptContextConstraintValidator.validate(normalized, templateOpt));

        return messages;
    }

    private static boolean isTemplateConstraintViolation(RMObjectValidationMessage message) {
        if (message.getType() != RMObjectValidationMessageType.DEFAULT) {
            return false;
        }
        if (message.getPath() == null) {
            return false;
        }
        String path = message.getPath();
        if (path.contains("/category/defining_code") || path.startsWith("/content")) {
            return false;
        }
        return true;
    }

    private static boolean hasWrapperContent(Composition composition) {
        String compositionArchetypeId = composition.getArchetypeNodeId();
        if (composition.getContent() == null || compositionArchetypeId == null) {
            return false;
        }
        return composition.getContent().stream()
                .filter(Locatable.class::isInstance)
                .map(Locatable.class::cast)
                .map(Locatable::getArchetypeNodeId)
                .filter(nodeId -> nodeId != null && !nodeId.equals(compositionArchetypeId))
                .anyMatch(nodeId -> nodeId.startsWith("openEHR-EHR-"));
    }
}
