/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.service;

import com.nedap.archie.aom.OperationalTemplate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.ehrbase.adl2.knowledge.Adl2KnowledgeService;
import org.ehrbase.api.exception.ObjectNotFoundException;
import org.ehrbase.api.knowledge.KnowledgeCacheService;
import org.ehrbase.api.knowledge.TemplateMetaData;
import org.ehrbase.api.service.Adl2TemplateService;
import org.ehrbase.openehr.sdk.response.dto.TemplateResponseData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class Adl2TemplateServiceImp implements Adl2TemplateService {

    private final KnowledgeCacheService knowledgeCacheService;
    private final Adl2KnowledgeService adl2KnowledgeService;

    public Adl2TemplateServiceImp(
            KnowledgeCacheService knowledgeCacheService, Adl2KnowledgeService adl2KnowledgeService) {
        this.knowledgeCacheService = Objects.requireNonNull(knowledgeCacheService);
        this.adl2KnowledgeService = Objects.requireNonNull(adl2KnowledgeService);
    }

    @Override
    public String create(String adl2Source) {
        OperationalTemplate opt = adl2KnowledgeService.parseTemplateSource(adl2Source);
        String templateId = adl2KnowledgeService.resolveTemplateId(opt);
        String optJson = adl2KnowledgeService.serializeOptJson(opt);
        String storedSource = adl2Source.trim().startsWith("{")
                ? adl2KnowledgeService.serializeAdl(opt)
                : adl2Source;
        return knowledgeCacheService.addAdl2OperationalTemplate(templateId, storedSource, optJson);
    }

    @Override
    public List<TemplateResponseData> listTemplates() {
        return knowledgeCacheService.listAllOperationalTemplates().stream()
                .filter(TemplateMetaData::isAdl2)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public String findAdl2Template(String templateId, String versionPattern) {
        return knowledgeCacheService
                .retrieveAdl2OperationalTemplateAdl(templateId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "template", "ADL2 template with the specified id does not exist: " + templateId));
    }

    @Override
    public String findAdl2OptJson(String templateId) {
        return knowledgeCacheService
                .retrieveAdl2OperationalTemplate(templateId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "template", "ADL2 template with the specified id does not exist: " + templateId));
    }

    private TemplateResponseData toDto(TemplateMetaData meta) {
        String templateId = meta.getTemplateId();
        if (templateId == null && meta.getAdl2OptJson() != null) {
            templateId = adl2KnowledgeService.resolveTemplateId(
                    adl2KnowledgeService.deserializeOptJson(meta.getAdl2OptJson()));
        }
        return new TemplateResponseData(templateId);
    }
}
