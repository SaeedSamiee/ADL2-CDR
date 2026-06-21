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
package org.ehrbase.rest.openehr;

import com.nedap.archie.rm.composition.Composition;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.apache.commons.io.IOUtils;
import org.apache.xmlbeans.XmlException;
import org.ehrbase.api.definitions.OperationalTemplateFormat;
import org.ehrbase.api.exception.InvalidApiParameterException;
import org.ehrbase.api.exception.NotAcceptableException;
import org.ehrbase.api.service.Adl2TemplateService;
import org.ehrbase.api.service.CompositionService;
import org.ehrbase.api.service.TemplateService;
import org.ehrbase.openehr.sdk.response.dto.TemplateResponseData;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.TemplateMetaDataDto;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.rest.BaseController;
import org.ehrbase.rest.openehr.format.CompositionRepresentation;
import org.ehrbase.rest.openehr.format.OpenEHRMediaType;
import org.ehrbase.rest.openehr.specification.TemplateApiSpecification;
import org.openehr.schemas.v1.TemplateDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

/**
 * Controller for /template resource as part of the Definitions sub-API of the openEHR REST API
 */
@ConditionalOnMissingBean(name = "primaryopenehrtemplatecontroller")
@RestController
@RequestMapping(path = BaseController.API_CONTEXT_PATH_WITH_VERSION + "/definition/template")
public class OpenehrTemplateController extends BaseController implements TemplateApiSpecification {

    protected static final String ADL_1_4 = "adl1.4";
    protected static final String ADL_2 = "adl2";

    private final TemplateService templateService;
    private final CompositionService compositionService;
    private final Adl2TemplateService adl2TemplateService;

    @Autowired
    public OpenehrTemplateController(
            TemplateService templateService,
            CompositionService compositionService,
            Adl2TemplateService adl2TemplateService) {
        this.templateService = Objects.requireNonNull(templateService);
        this.compositionService = Objects.requireNonNull(compositionService);
        this.adl2TemplateService = Objects.requireNonNull(adl2TemplateService);
    }

    /*
       ADL 1.4
    */
    @PostMapping(
            path = "/adl1.4",
            consumes = {MediaType.APPLICATION_XML_VALUE},
            produces = {MediaType.APPLICATION_XML_VALUE})
    @ResponseStatus(value = HttpStatus.CREATED)
    public ResponseEntity<String> createTemplateClassic(
            @RequestHeader(value = OPENEHR_VERSION, required = false) String openehrVersion,
            @RequestHeader(value = OPENEHR_AUDIT_DETAILS, required = false) String openehrAuditDetails,
            @RequestHeader(value = PREFER, required = false) String prefer,
            @RequestBody String template) {

        // create template
        String templateId;
        try (var in = IOUtils.toInputStream(template, StandardCharsets.UTF_8)) {
            TemplateDocument document = TemplateDocument.Factory.parse(in);
            templateId = templateService.create(document.getTemplate());
        } catch (XmlException | IOException e) {
            throw new InvalidApiParameterException(e.getMessage());
        }

        // initialize HTTP 201 Created body builder
        ResponseEntity.BodyBuilder bodyBuilder =
                templateResponseBuilder(HttpStatus.CREATED, templateId, MediaType.APPLICATION_XML);

        // return either representation body or only the created response
        if (RETURN_REPRESENTATION.equals(prefer)) {
            String responseTemplate =
                    templateService.findOperationalTemplate(templateId, OperationalTemplateFormat.XML);
            return bodyBuilder.body(responseTemplate);
        } else {
            return bodyBuilder.build();
        }
    }

    // Note: based on latest-branch of 1.1.0 release of openEHR REST API, because this endpoint was changed
    // significantly
    @GetMapping(
            value = "/adl1.4",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    public ResponseEntity<List<TemplateMetaDataDto>> getTemplatesClassic(
            @RequestHeader(value = OPENEHR_VERSION, required = false) String openehrVersion,
            @RequestHeader(value = OPENEHR_AUDIT_DETAILS, required = false) String openehrAuditDetails,
            @RequestHeader(value = ACCEPT, required = false) String accept) {

        URI uri = createLocationUri(DEFINITION, TEMPLATE, ADL_1_4);
        MediaType mediaType = resolveContentType(accept, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML);

        List<TemplateMetaDataDto> templates = templateService.getAllTemplates();

        // returns 200 with all templates
        return ResponseEntity.ok().location(uri).contentType(mediaType).body(templates);
    }

    // Note: based on latest-branch of 1.1.0 release of openEHR REST API, because this endpoint was changed
    // significantly
    @GetMapping(
            value = "/adl1.4/{template_id}",
            // TODO ITS-REST/Release-1.0.3 produces also "text/plain" but what format should this be
            produces = {
                MediaType.APPLICATION_XML_VALUE,
                MediaType.APPLICATION_JSON_VALUE,
                OpenEHRMediaType.APPLICATION_WT_JSON_VALUE
            })
    public ResponseEntity<Object> getTemplateClassic(
            @RequestHeader(value = OPENEHR_VERSION, required = false) String openehrVersion,
            @RequestHeader(value = OPENEHR_AUDIT_DETAILS, required = false) String openehrAuditDetails,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @PathVariable(value = "template_id") String templateId) {

        // parse and set accepted format. with XML as fallback for empty header and error for unsupported header
        MediaType mediaType = resolveContentType(
                accept, MediaType.APPLICATION_XML, OpenEHRMediaType.APPLICATION_WT_JSON, MediaType.APPLICATION_JSON);

        // resolve template
        OperationalTemplateFormat format = operationalTemplateFormatForMediaType(mediaType);

        ResponseEntity.BodyBuilder bodyBuilder = templateResponseBuilder(HttpStatus.OK, templateId, mediaType);

        // return the original XML based OPT format (if called with the Accept: application/xml request header),
        if (mediaType.isCompatibleWith(MediaType.APPLICATION_XML)) {
            String operationalTemplate = templateService.findOperationalTemplate(templateId, format);
            return bodyBuilder.body(operationalTemplate);
        }
        // return simplified JSON-based “web template” format (if called with the Accept: application/json or
        // application/openehr.wt+json request header)
        else {
            final WebTemplate webTemplate = templateService.findWebTemplate(templateId);
            return bodyBuilder.body(webTemplate);
        }
    }

    @GetMapping(
            path = "/adl1.4/{template_id}/example",
            produces = {
                MediaType.APPLICATION_JSON_VALUE,
                MediaType.APPLICATION_XML_VALUE,
                OpenEHRMediaType.APPLICATION_WT_STRUCTURED_SCHEMA_JSON_VALUE,
                OpenEHRMediaType.APPLICATION_WT_FLAT_SCHEMA_JSON_VALUE
            })
    public ResponseEntity<String> getTemplateExample(
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @PathVariable(value = "template_id") String templateId,
            @RequestParam(value = "format", required = false) String format) {

        CompositionRepresentation representation = extractCompositionRepresentation(accept, format);
        Composition composition = templateService.buildExample(templateId);

        return ResponseEntity.ok()
                .location(createLocationUri(DEFINITION, TEMPLATE, ADL_1_4, templateId, "example"))
                .contentType(representation.mediaType)
                .body(compositionService
                        .serialize(composition, representation.format)
                        .getValue());
    }

    @GetMapping(
            path = "/adl1.4/{template_id}/webtemplate",
            produces = {MediaType.APPLICATION_JSON_VALUE, OpenEHRMediaType.APPLICATION_WT_JSON_VALUE})
    @SuppressWarnings({"removal", "UastIncorrectHttpHeaderInspection"})
    public ResponseEntity<WebTemplate> getWebTemplate(
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @PathVariable(value = "template_id") String templateId) {

        final MediaType mediaType =
                resolveContentType(accept, OpenEHRMediaType.APPLICATION_WT_JSON, MediaType.APPLICATION_JSON);
        final WebTemplate webTemplate = templateService.findWebTemplate(templateId);

        // @format:off
        final String linkPrefix = "%s/%s".formatted(getContextPath(), "swagger-ui/index.html?urls.primaryName=1.%20openEHR%20API#");

        return ResponseEntity.ok()
                .location(createLocationUri(DEFINITION, TEMPLATE, ADL_1_4, templateId, "webtemplate"))
                .contentType(mediaType)
                .header("Deprecated", "Mon, 03 Jun 2024 00:00:00 GMT")
                // .headers("Sunset", "Tue, 31 Dec 2024 00:00:00 GMT"); <- could be used until we know it ;)
                .header("Link", String.join(", ", List.of(
                    "<%s/%s>; rel=\"deprecation\"; type=\"text/html\"".formatted(linkPrefix, UriUtils.encode("TEMPLATE/getWebTemplate", StandardCharsets.US_ASCII)),
                    "<%s/%s>; rel=\"successor-version\"".formatted(linkPrefix, UriUtils.encode("ADL 1.4 TEMPLATE/getTemplateClassic", StandardCharsets.US_ASCII))
                )))
                .body(webTemplate);
        // @format:on
    }

    /*
       ADL 2 — implemented via Archie 2 knowledge layer
    */
    @PostMapping(
            value = "/adl2",
            consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @ResponseStatus(value = HttpStatus.CREATED)
    public ResponseEntity<String> createTemplateNew(
            @RequestHeader(value = OPENEHR_VERSION, required = false) String openehrVersion,
            @RequestHeader(value = OPENEHR_AUDIT_DETAILS, required = false) String openehrAuditDetails,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @RequestHeader(value = PREFER, required = false) String prefer,
            @RequestParam(value = "version", required = false) String version,
            @RequestBody String template) {

        String templateId = adl2TemplateService.create(template);
        MediaType mediaType = resolveContentType(accept, MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
        ResponseEntity.BodyBuilder bodyBuilder = adl2TemplateResponseBuilder(HttpStatus.CREATED, templateId, mediaType);
        if (RETURN_REPRESENTATION.equals(prefer)) {
            return bodyBuilder.body(adl2TemplateService.findAdl2Template(templateId, version));
        }
        return bodyBuilder.build();
    }

    @GetMapping(
            value = "/adl2",
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<TemplateResponseData>> getTemplatesNew(
            @RequestHeader(value = OPENEHR_VERSION, required = false) String openehrVersion,
            @RequestHeader(value = OPENEHR_AUDIT_DETAILS, required = false) String openehrAuditDetails,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {

        URI uri = createLocationUri(DEFINITION, TEMPLATE, ADL_2);
        return ResponseEntity.ok()
                .location(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(adl2TemplateService.listTemplates());
    }

    @GetMapping(
            value = "/adl2/{template_id}/{version_pattern}",
            produces = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> getTemplateNew(
            @RequestHeader(value = OPENEHR_VERSION, required = false) String openehrVersion,
            @RequestHeader(value = OPENEHR_AUDIT_DETAILS, required = false) String openehrAuditDetails,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @PathVariable(value = "template_id", required = false) String templateId,
            @PathVariable(value = "version_pattern", required = false) String versionPattern) {

        MediaType mediaType = resolveContentType(accept, MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
        String body = adl2TemplateService.findAdl2Template(templateId, versionPattern);
        return adl2TemplateResponseBuilder(HttpStatus.OK, templateId, mediaType).body(body);
    }

    private ResponseEntity.BodyBuilder adl2TemplateResponseBuilder(
            HttpStatus status, String templateId, MediaType mediaType) {

        URI uri = createLocationUri(DEFINITION, TEMPLATE, ADL_2, templateId);

        return ResponseEntity.status(status)
                .location(uri)
                .contentType(mediaType)
                .eTag("\"%s\"".formatted(templateId))
                .lastModified(123124442);
    }

    private ResponseEntity.BodyBuilder templateResponseBuilder(
            HttpStatus status, String templateId, MediaType mediaType) {

        URI uri = createLocationUri(DEFINITION, TEMPLATE, ADL_1_4, templateId);

        // initialize HTTP 201 Created body builder
        return ResponseEntity.status(status)
                .location(uri)
                .contentType(mediaType)
                .eTag("\"%s\"".formatted(templateId))
                // TODO should be VERSION.commit_audit.time_committed.value which is not implemented yet - mock for now
                .lastModified(123124442);
    }

    private static OperationalTemplateFormat operationalTemplateFormatForMediaType(MediaType mediaType) {
        if (mediaType.isCompatibleWith(MediaType.APPLICATION_XML)) {
            return OperationalTemplateFormat.XML;
        } else if (mediaType.isCompatibleWith(MediaType.APPLICATION_JSON)
                || mediaType.isCompatibleWith(OpenEHRMediaType.APPLICATION_WT_JSON)) {
            return OperationalTemplateFormat.JSON;
        } else {
            throw new NotAcceptableException(
                    "Operation templates are only available in XML based OPT or simplified JSON-based web template format");
        }
    }
}
