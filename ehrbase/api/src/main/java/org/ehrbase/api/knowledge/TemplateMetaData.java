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
package org.ehrbase.api.knowledge;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;

public class TemplateMetaData {
    private OPERATIONALTEMPLATE operationaltemplate;
    private OffsetDateTime createdOn;

    private UUID internalId;

    private List<String> errorList;

    /** ADL2 OPT serialized as Archie JSON when {@link #templateFormat} is {@link TemplateFormat#ADL2}. */
    private String adl2OptJson;

    /** Original ADL2 ADL source text when stored separately from JSON. */
    private String adl2Source;

    private String templateId;

    private TemplateFormat templateFormat = TemplateFormat.ADL14;

    public OPERATIONALTEMPLATE getOperationaltemplate() {
        return operationaltemplate;
    }

    public void setOperationalTemplate(OPERATIONALTEMPLATE operationaltemplate) {
        this.operationaltemplate = operationaltemplate;
    }

    public OffsetDateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(OffsetDateTime createdOn) {
        this.createdOn = createdOn;
    }

    public void setInternalId(UUID internalId) {
        this.internalId = internalId;
    }

    public UUID getInternalId() {
        return internalId;
    }

    public List<String> getErrorList() {
        if (this.errorList == null) {
            this.errorList = new ArrayList<>();
        }
        return errorList;
    }

    public void addError(String error) {
        if (this.errorList == null) {
            this.errorList = new ArrayList<>();
        }
        this.errorList.add(error);
    }

    public String getAdl2OptJson() {
        return adl2OptJson;
    }

    public void setAdl2OptJson(String adl2OptJson) {
        this.adl2OptJson = adl2OptJson;
    }

    public String getAdl2Source() {
        return adl2Source;
    }

    public void setAdl2Source(String adl2Source) {
        this.adl2Source = adl2Source;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public TemplateFormat getTemplateFormat() {
        return templateFormat == null ? TemplateFormat.ADL14 : templateFormat;
    }

    public void setTemplateFormat(TemplateFormat templateFormat) {
        this.templateFormat = templateFormat;
    }

    public boolean isAdl2() {
        return getTemplateFormat() == TemplateFormat.ADL2;
    }
}
