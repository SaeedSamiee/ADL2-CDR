/*
 * Copyright (c) 2024 vitasystems GmbH.
 *
 * This file is part of project EHRbase
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ehrbase.repository;

import static org.ehrbase.jooq.pg.Tables.COMP_VERSION;
import static org.ehrbase.jooq.pg.tables.TemplateStore.TEMPLATE_STORE;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;
import org.apache.commons.io.IOUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.ehrbase.api.exception.InternalServerException;
import org.ehrbase.api.exception.ObjectNotFoundException;
import org.ehrbase.api.knowledge.TemplateFormat;
import org.ehrbase.api.knowledge.TemplateMetaData;
import org.ehrbase.jooq.pg.tables.records.TemplateStoreRecord;
import org.ehrbase.service.TimeProvider;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Record6;
import org.jooq.impl.DSL;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.springframework.stereotype.Repository;

@Repository
public class TemplateStoreRepository {

    private static final Field<String> TEMPLATE_FORMAT = DSL.field("template_format", String.class);
    private static final Field<String> OPT_ADL2_JSON = DSL.field("opt_adl2_json", String.class);

    private final DSLContext context;
    private final TimeProvider timeProvider;

    public TemplateStoreRepository(DSLContext context, TimeProvider timeProvider) {
        this.context = context;
        this.timeProvider = timeProvider;
    }

    public TemplateMetaData store(OPERATIONALTEMPLATE operationaltemplate) {
        TemplateStoreRecord templateStoreRecord = context.newRecord(TEMPLATE_STORE);
        setTemplate(operationaltemplate, templateStoreRecord, rec -> rec.setId(UUID.randomUUID()));
        templateStoreRecord.setCreationTime(timeProvider.getNow());
        templateStoreRecord.store();
        return TemplateStoreRepository.buildMetadata(
                templateStoreRecord.getId(),
                templateStoreRecord.getCreationTime(),
                templateStoreRecord.getContent(),
                TemplateFormat.ADL14.wireValue(),
                null,
                templateStoreRecord.getTemplateId());
    }

    public TemplateMetaData storeAdl2(String templateId, String adl2Source, String adl2OptJson) {
        TemplateStoreRecord templateStoreRecord = context.newRecord(TEMPLATE_STORE);
        UUID id = UUID.randomUUID();
        templateStoreRecord.setId(id);
        templateStoreRecord.setTemplateId(templateId);
        templateStoreRecord.setContent(adl2Source);
        templateStoreRecord.setCreationTime(timeProvider.getNow());
        templateStoreRecord.store();
        context.update(TEMPLATE_STORE)
                .set(TEMPLATE_FORMAT, TemplateFormat.ADL2.wireValue())
                .set(OPT_ADL2_JSON, adl2OptJson)
                .where(TEMPLATE_STORE.ID.eq(id))
                .execute();
        return TemplateStoreRepository.buildMetadata(
                id, templateStoreRecord.getCreationTime(), adl2Source, TemplateFormat.ADL2.wireValue(), adl2OptJson, templateId);
    }

    public TemplateMetaData updateAdl2(String templateId, String adl2Source, String adl2OptJson) {
        TemplateStoreRecord templateStoreRecord = context.selectFrom(TEMPLATE_STORE)
                .where(TEMPLATE_STORE.TEMPLATE_ID.eq(templateId))
                .fetchOptional()
                .orElseThrow(() -> new ObjectNotFoundException(
                        "OPERATIONALTEMPLATE", "No template with id = %s".formatted(templateId)));

        templateStoreRecord.setContent(adl2Source);
        templateStoreRecord.setCreationTime(timeProvider.getNow());
        templateStoreRecord.update();
        context.update(TEMPLATE_STORE)
                .set(TEMPLATE_FORMAT, TemplateFormat.ADL2.wireValue())
                .set(OPT_ADL2_JSON, adl2OptJson)
                .where(TEMPLATE_STORE.ID.eq(templateStoreRecord.getId()))
                .execute();
        return TemplateStoreRepository.buildMetadata(
                templateStoreRecord.getId(),
                templateStoreRecord.getCreationTime(),
                adl2Source,
                TemplateFormat.ADL2.wireValue(),
                adl2OptJson,
                templateId);
    }

    public TemplateMetaData update(OPERATIONALTEMPLATE operationaltemplate) {
        String templateId = operationaltemplate.getTemplateId().getValue();
        TemplateStoreRecord templateStoreRecord = context.selectFrom(TEMPLATE_STORE)
                .where(TEMPLATE_STORE.TEMPLATE_ID.eq(templateId))
                .fetchOptional()
                .orElseThrow(() -> new ObjectNotFoundException(
                        "OPERATIONALTEMPLATE", "No template with id = %s".formatted(templateId)));

        setTemplate(operationaltemplate, templateStoreRecord, rec -> rec.setId(rec.getId()));
        templateStoreRecord.setCreationTime(timeProvider.getNow());
        templateStoreRecord.update();
        return TemplateStoreRepository.buildMetadata(
                templateStoreRecord.getId(),
                templateStoreRecord.getCreationTime(),
                templateStoreRecord.getContent(),
                TemplateFormat.ADL14.wireValue(),
                null,
                templateId);
    }

    public List<TemplateMetaData> findAll() {

        return context.select(
                        TEMPLATE_STORE.CONTENT,
                        TEMPLATE_STORE.CREATION_TIME,
                        TEMPLATE_STORE.ID,
                        TEMPLATE_FORMAT,
                        OPT_ADL2_JSON,
                        TEMPLATE_STORE.TEMPLATE_ID)
                .from(TEMPLATE_STORE)
                .fetch()
                .map(TemplateStoreRepository::buildMetadata);
    }

    public Map<UUID, String> findAllTemplateIds() {
        return context.select(TEMPLATE_STORE.ID, TEMPLATE_STORE.TEMPLATE_ID)
                .from(TEMPLATE_STORE)
                .collect(Collectors.toMap(Record2::value1, Record2::value2));
    }

    private static TemplateMetaData buildMetadata(Record6<String, OffsetDateTime, UUID, String, String, String> r) {
        return buildMetadata(r.component3(), r.component2(), r.component1(), r.component4(), r.component5(), r.component6());
    }

    private static TemplateMetaData buildMetadata(
            UUID internalId,
            OffsetDateTime creationTime,
            String templateContent,
            String templateFormat,
            String adl2OptJson,
            String templateId) {
        TemplateMetaData templateMetaData = new TemplateMetaData();
        templateMetaData.setTemplateId(templateId);
        templateMetaData.setTemplateFormat(TemplateFormat.fromWire(templateFormat));
        templateMetaData.setAdl2OptJson(adl2OptJson);
        if (templateMetaData.isAdl2()) {
            templateMetaData.setAdl2Source(templateContent);
            templateMetaData.setOperationalTemplate(null);
        } else {
            templateMetaData.setOperationalTemplate(buildOperationalTemplate(templateContent));
        }
        templateMetaData.setCreatedOn(creationTime);
        templateMetaData.setInternalId(internalId);
        return templateMetaData;
    }

    public void delete(String templateId) {

        int execute = context.deleteFrom(TEMPLATE_STORE)
                .where(TEMPLATE_STORE.TEMPLATE_ID.eq(templateId))
                .execute();

        if (execute == 0) {
            throw new ObjectNotFoundException("OPERATIONALTEMPLATE", "No template with id = %s".formatted(templateId));
        }
    }

    public Optional<TemplateMetaData> findByTemplateId(String templateId) {

        return context.select(
                        TEMPLATE_STORE.CONTENT,
                        TEMPLATE_STORE.CREATION_TIME,
                        TEMPLATE_STORE.ID,
                        TEMPLATE_FORMAT,
                        OPT_ADL2_JSON,
                        TEMPLATE_STORE.TEMPLATE_ID)
                .from(TEMPLATE_STORE)
                .where(TEMPLATE_STORE.TEMPLATE_ID.eq(templateId))
                .fetchOptional()
                .map(TemplateStoreRepository::buildMetadata);
    }

    public Optional<String> findTemplateIdByUuid(UUID uuid) {
        return context.select(TEMPLATE_STORE.TEMPLATE_ID)
                .from(TEMPLATE_STORE)
                .where(TEMPLATE_STORE.ID.eq(uuid))
                .fetchOptional(TEMPLATE_STORE.TEMPLATE_ID);
    }

    public Optional<UUID> findUuidByTemplateId(String templateId) {
        return context.select(TEMPLATE_STORE.ID)
                .from(TEMPLATE_STORE)
                .where(TEMPLATE_STORE.TEMPLATE_ID.eq(templateId))
                .fetchOptional(TEMPLATE_STORE.ID);
    }

    private static OPERATIONALTEMPLATE buildOperationalTemplate(String content) {
        org.openehr.schemas.v1.TemplateDocument document;
        try (InputStream in = IOUtils.toInputStream(content, StandardCharsets.UTF_8)) {
            document = org.openehr.schemas.v1.TemplateDocument.Factory.parse(in);
        } catch (XmlException | IOException e) {
            throw new InternalServerException(e.getMessage());
        }

        return document.getTemplate();
    }

    private static void setTemplate(
            OPERATIONALTEMPLATE template,
            TemplateStoreRecord templateStoreRecord,
            Consumer<TemplateStoreRecord> setId) {
        setId.accept(templateStoreRecord);
        templateStoreRecord.setTemplateId(template.getTemplateId().getValue());
        XmlOptions opts = new XmlOptions();
        opts.setSaveSyntheticDocumentElement(new QName("http://schemas.openehr.org/v1", "template"));
        templateStoreRecord.setContent(template.xmlText(opts));
    }

    public List<String> getTemplateUsages() {
        return context.selectDistinct(TEMPLATE_STORE.TEMPLATE_ID)
                .from(TEMPLATE_STORE)
                .join(COMP_VERSION)
                .on(COMP_VERSION.TEMPLATE_ID.eq(TEMPLATE_STORE.ID))
                .fetch(TEMPLATE_STORE.TEMPLATE_ID);
    }
}
