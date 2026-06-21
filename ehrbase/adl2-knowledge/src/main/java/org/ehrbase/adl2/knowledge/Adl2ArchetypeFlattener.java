/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

import com.nedap.archie.adlparser.ADLParser;
import com.nedap.archie.aom.Archetype;
import com.nedap.archie.aom.OperationalTemplate;
import com.nedap.archie.flattener.Flattener;
import com.nedap.archie.flattener.FlattenerConfiguration;
import com.nedap.archie.flattener.InMemoryFullArchetypeRepository;
import com.nedap.archie.rm.archetyped.Pathable;
import com.nedap.archie.rmobjectvalidator.ValidationConfiguration;
import com.nedap.archie.rules.evaluation.EvaluationResult;
import com.nedap.archie.rules.evaluation.RuleEvaluation;
import com.nedap.archie.rminfo.ArchieRMInfoLookup;
import com.nedap.archie.rminfo.MetaModels;
import com.nedap.archie.serializer.adl.ADLArchetypeSerializer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.openehr.referencemodels.BuiltinReferenceModels;

/** Utilities for flattening Archie test archetypes and evaluating ADL rules. */
public final class Adl2ArchetypeFlattener {

    private Adl2ArchetypeFlattener() {}

    public static OperationalTemplate parseOperationalTemplate(String optAdl) {
        try {
            ADLParser parser = new ADLParser();
            return (OperationalTemplate) parser.parse(new ByteArrayInputStream(optAdl.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new Adl2KnowledgeException("Failed to parse operational template ADL", e);
        }
    }

    public static OperationalTemplate flattenArchetypeToOpt(String archetypeAdl) {
        try {
            MetaModels metaModels = BuiltinReferenceModels.getMetaModels();
            ADLParser parser = new ADLParser(metaModels);
            InMemoryFullArchetypeRepository repository = new InMemoryFullArchetypeRepository();
            Archetype archetype =
                    parser.parse(new ByteArrayInputStream(archetypeAdl.getBytes(StandardCharsets.UTF_8)));
            repository.addArchetype(archetype);
            repository.compile(metaModels);
            return (OperationalTemplate) new Flattener(
                            repository, metaModels, FlattenerConfiguration.forOperationalTemplate())
                    .flatten(archetype);
        } catch (Exception e) {
            throw new Adl2KnowledgeException("Failed to flatten archetype to operational template", e);
        }
    }

    public static String serializeOptAdl(OperationalTemplate opt) {
        return ADLArchetypeSerializer.serialize(opt);
    }

    public static EvaluationResult evaluateRules(Pathable root, OperationalTemplate opt) {
        RuleEvaluation<Pathable> ruleEvaluation = new RuleEvaluation<>(
                ArchieRMInfoLookup.getInstance(), new ValidationConfiguration.Builder().build(), opt);
        ruleEvaluation.evaluate(root, opt.getRules().getRules());
        return ruleEvaluation.getEvaluationResult();
    }

    public static boolean assertionPassed(EvaluationResult evaluationResult, String assertionTag) {
        return evaluationResult.getAssertionResults().stream()
                .filter(result -> assertionTag.equals(result.getTag()))
                .map(result -> Boolean.TRUE.equals(result.getResult()))
                .findFirst()
                .orElseThrow(() -> new Adl2KnowledgeException("Assertion not found: " + assertionTag));
    }
}
