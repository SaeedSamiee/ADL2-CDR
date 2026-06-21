/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.api.service;

import java.util.List;
import org.ehrbase.openehr.sdk.response.dto.TemplateResponseData;

public interface Adl2TemplateService {

    String create(String adl2Source);

    List<TemplateResponseData> listTemplates();

    String findAdl2Template(String templateId, String versionPattern);

    String findAdl2OptJson(String templateId);
}
