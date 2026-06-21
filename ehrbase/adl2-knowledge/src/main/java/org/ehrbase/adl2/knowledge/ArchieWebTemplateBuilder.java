/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

import com.nedap.archie.ArchieLanguageConfiguration;
import com.nedap.archie.aom.CAttribute;
import com.nedap.archie.aom.CObject;
import com.nedap.archie.aom.OperationalTemplate;
import com.nedap.archie.base.MultiplicityInterval;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplateNode;

/**
 * Builds openEHR SDK {@link WebTemplate} trees from Archie ADL2 {@link OperationalTemplate} constraints.
 */
public class ArchieWebTemplateBuilder {

    public WebTemplate build(OperationalTemplate opt, String language) {
        ArchieLanguageConfiguration.setThreadLocalDescriptiongAndMeaningLanguage(language);
        try {
            WebTemplate webTemplate = new WebTemplate();
            if (opt.getArchetypeId() != null) {
                webTemplate.setTemplateId(opt.getArchetypeId().getFullId());
                webTemplate.setVersion(opt.getArchetypeId().getVersionId());
            }
            webTemplate.setDefaultLanguage(language);

            WebTemplateNode root = new WebTemplateNode();
            root.setId("root");
            root.setName(resolveTemplateName(opt));
            root.setRmType("COMPOSITION");
            root.setMin(1);
            root.setMax(1);
            if (opt.getDefinition() != null) {
                root.getChildren().add(walkObject(opt.getDefinition(), language, ""));
            }
            webTemplate.setTree(root);
            return webTemplate;
        } finally {
            ArchieLanguageConfiguration.setThreadLocalDescriptiongAndMeaningLanguage(null);
        }
    }

    private WebTemplateNode walkObject(CObject cObject, String language, String pathPrefix) {
        WebTemplateNode node = new WebTemplateNode();
        node.setId(cObject.getNodeId());
        node.setNodeId(cObject.getNodeId());
        node.setName(resolveName(cObject));
        node.setRmType(cObject.getRmTypeName());
        node.setMin(resolveMin(cObject));
        node.setMax(resolveMax(cObject));
        node.setAqlPath(buildAqlPath(pathPrefix, cObject));

        if (cObject.getAttributes() != null) {
            for (CAttribute attribute : cObject.getAttributes()) {
                if (attribute.getChildren() == null) {
                    continue;
                }
                for (CObject child : attribute.getChildren()) {
                    node.getChildren().add(walkObject(child, language, node.getAqlPath()));
                }
            }
        }
        return node;
    }

    private static String resolveTemplateName(OperationalTemplate opt) {
        if (opt.getDefinition() != null && opt.getDefinition().getMeaning() != null) {
            return opt.getDefinition().getMeaning();
        }
        if (opt.getArchetypeId() != null) {
            return opt.getArchetypeId().getFullId();
        }
        return "template";
    }

    private static String buildAqlPath(String prefix, CObject cObject) {
        String segment = cObject.getNodeId() != null && !cObject.getNodeId().isBlank()
                ? cObject.getNodeId()
                : cObject.getRmTypeName().toLowerCase();
        if (prefix == null || prefix.isBlank()) {
            return segment;
        }
        return prefix + "/" + segment;
    }

    private static String resolveName(CObject cObject) {
        if (cObject.getMeaning() != null && !cObject.getMeaning().isBlank()) {
            return cObject.getMeaning();
        }
        if (cObject.getNodeId() != null) {
            return cObject.getNodeId();
        }
        return cObject.getRmTypeName();
    }

    private static int resolveMin(CObject cObject) {
        MultiplicityInterval occurrences = cObject.getOccurrences();
        if (occurrences == null || occurrences.getLower() == null) {
            return 0;
        }
        return occurrences.getLower();
    }

    private static int resolveMax(CObject cObject) {
        MultiplicityInterval occurrences = cObject.getOccurrences();
        if (occurrences == null) {
            return -1;
        }
        if (occurrences.isUpperUnbounded()) {
            return -1;
        }
        if (occurrences.getUpper() == null) {
            return -1;
        }
        return occurrences.getUpper();
    }
}
