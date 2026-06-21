/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nedap.archie.adlparser.ADLParser;
import com.nedap.archie.aom.Archetype;
import com.nedap.archie.aom.OperationalTemplate;
import com.nedap.archie.json.ArchieJacksonConfiguration;
import com.nedap.archie.json.JacksonUtil;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rmobjectvalidator.RMObjectValidationMessage;
import com.nedap.archie.rmobjectvalidator.RMObjectValidator;
import com.nedap.archie.rmobjectvalidator.ValidationConfiguration;
import com.nedap.archie.rminfo.ArchieRMInfoLookup;
import com.nedap.archie.serializer.adl.ADLArchetypeSerializer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;

public class Adl2KnowledgeServiceImpl implements Adl2KnowledgeService {

    private static final ObjectMapper OBJECT_MAPPER =
            JacksonUtil.getObjectMapper(ArchieJacksonConfiguration.createStandardsCompliant());

    private final ADLParser adlParser = new ADLParser();
    private final ArchieWebTemplateBuilder webTemplateBuilder = new ArchieWebTemplateBuilder();
    private final RMObjectValidator validator = new RMObjectValidator(
            ArchieRMInfoLookup.getInstance(), templateId -> null, new ValidationConfiguration.Builder().build());

    @Override
    public OperationalTemplate parseTemplateSource(String source) {
        if (source == null || source.isBlank()) {
            throw new Adl2KnowledgeException("Template source is empty");
        }
        String trimmed = source.trim();
        if (trimmed.startsWith("{")) {
            return deserializeOptJson(trimmed);
        }
        try {
            Archetype parsed = adlParser.parse(new ByteArrayInputStream(trimmed.getBytes(StandardCharsets.UTF_8)));
            if (!(parsed instanceof OperationalTemplate opt)) {
                throw new Adl2KnowledgeException(
                        "Expected ADL2 operational template, got: " + parsed.getClass().getSimpleName());
            }
            return opt;
        } catch (Adl2KnowledgeException e) {
            throw e;
        } catch (Exception e) {
            throw new Adl2KnowledgeException("Failed to parse ADL2 template: " + e.getMessage(), e);
        }
    }

    @Override
    public String serializeOptJson(OperationalTemplate opt) {
        try {
            return OBJECT_MAPPER.writeValueAsString(opt);
        } catch (Exception e) {
            throw new Adl2KnowledgeException("Failed to serialize ADL2 OPT to JSON", e);
        }
    }

    @Override
    public OperationalTemplate deserializeOptJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, OperationalTemplate.class);
        } catch (Adl2KnowledgeException e) {
            throw e;
        } catch (Exception e) {
            throw new Adl2KnowledgeException("Failed to deserialize ADL2 OPT JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public String resolveTemplateId(OperationalTemplate opt) {
        if (opt.getArchetypeId() != null && StringUtils.isNotBlank(opt.getArchetypeId().getFullId())) {
            return opt.getArchetypeId().getFullId();
        }
        throw new Adl2KnowledgeException("Template has no archetype_id");
    }

    @Override
    public WebTemplate buildWebTemplate(OperationalTemplate opt, String language) {
        return webTemplateBuilder.build(opt, language == null ? "en" : language);
    }

    @Override
    public List<RMObjectValidationMessage> validateComposition(Composition composition, OperationalTemplate opt) {
        return validator.validate(opt, composition);
    }

    @Override
    public String serializeAdl(OperationalTemplate opt) {
        return ADLArchetypeSerializer.serialize(opt);
    }
}
