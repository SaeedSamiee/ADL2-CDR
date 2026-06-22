/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

import com.nedap.archie.aom.OperationalTemplate;
import com.nedap.archie.rm.archetyped.Locatable;
import com.nedap.archie.rm.archetyped.Pathable;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rules.evaluation.EvaluationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Evaluates ADL rules on a composition and nested component archetypes. */
public final class Adl2CompositionRuleValidator {

    private Adl2CompositionRuleValidator() {}

    /**
     * Validates ADL rule assertions for the template OPT and for nested content whose
     * {@code archetype_node_id} resolves to another registered ADL2 OPT (e.g. observation rules inside a
     * composition wrapper).
     */
    public static List<Adl2RuleViolation> validate(
            Composition composition,
            OperationalTemplate templateOpt,
            Function<String, Optional<OperationalTemplate>> componentOptLookup) {
        List<Adl2RuleViolation> violations = new ArrayList<>();
        collectRuleViolations(composition, templateOpt, violations);

        if (composition.getContent() != null) {
            String primaryTemplateId =
                    templateOpt.getArchetypeId() != null ? templateOpt.getArchetypeId().getFullId() : null;
            for (var item : composition.getContent()) {
                if (item instanceof Locatable locatable) {
                    String archetypeNodeId = locatable.getArchetypeNodeId();
                    if (archetypeNodeId == null || archetypeNodeId.equals(primaryTemplateId)) {
                        continue;
                    }
                    componentOptLookup.apply(archetypeNodeId).ifPresent(opt -> collectRuleViolations(locatable, opt, violations));
                }
            }
        }
        return violations;
    }

    private static void collectRuleViolations(
            Pathable root, OperationalTemplate opt, List<Adl2RuleViolation> violations) {
        if (opt.getRules() == null || opt.getRules().getRules().isEmpty()) {
            return;
        }
        EvaluationResult result = Adl2ArchetypeFlattener.evaluateRules(root, opt);
        String templateId = opt.getArchetypeId() != null ? opt.getArchetypeId().getFullId() : null;
        result.getAssertionResults().stream()
                .filter(assertion -> Boolean.FALSE.equals(assertion.getResult()))
                .forEach(assertion -> violations.add(new Adl2RuleViolation(
                        assertion.getTag(),
                        "ADL rule assertion failed"
                                + (assertion.getTag() != null ? ": " + assertion.getTag() : "")
                                + (templateId != null ? " (template " + templateId + ")" : ""),
                        templateId)));
    }
}
