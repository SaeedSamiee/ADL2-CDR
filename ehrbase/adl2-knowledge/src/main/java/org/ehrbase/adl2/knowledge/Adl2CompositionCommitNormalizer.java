/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

import com.nedap.archie.aom.ArchetypeSlot;
import com.nedap.archie.aom.CAttribute;
import com.nedap.archie.aom.CComplexObject;
import com.nedap.archie.aom.CObject;
import com.nedap.archie.aom.OperationalTemplate;
import com.nedap.archie.rm.archetyped.Locatable;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datatypes.CodePhrase;
import com.nedap.archie.util.CloneUtil;
import java.util.HashMap;
import java.util.Map;
import org.ehrbase.openehr.sdk.webtemplate.parser.ArchieOptParser;

/** Aligns example/commit compositions with ADL2 OPT expectations for Archie validation. */
public final class Adl2CompositionCommitNormalizer {

    private Adl2CompositionCommitNormalizer() {}

    public static Composition normalize(Composition composition, OperationalTemplate templateOpt) {
        Composition normalized = CloneUtil.clone(composition);
        normalizeCategory(normalized);
        normalizeContentSlots(normalized, templateOpt);
        return normalized;
    }

    private static void normalizeCategory(Composition composition) {
        if (composition.getCategory() == null || composition.getCategory().getDefiningCode() == null) {
            return;
        }
        CodePhrase code = composition.getCategory().getDefiningCode();
        if (code.getTerminologyId() != null
                && "openehr".equals(code.getTerminologyId().getValue())
                && "431".equals(code.getCodeString())) {
            code.setCodeString("at1");
        }
    }

    private static void normalizeContentSlots(Composition composition, OperationalTemplate templateOpt) {
        Map<String, String> slotArchetypeIds = slotArchetypeIdsByLocalNodeId(templateOpt);
        if (composition.getContent() == null || slotArchetypeIds.isEmpty()) {
            return;
        }
        for (var item : composition.getContent()) {
            if (!(item instanceof Locatable locatable)) {
                continue;
            }
            String localNodeId = locatable.getArchetypeNodeId();
            if (localNodeId == null) {
                continue;
            }
            String resolvedArchetypeId = slotArchetypeIds.get(localNodeId);
            if (resolvedArchetypeId != null) {
                locatable.setArchetypeNodeId(resolvedArchetypeId);
            }
        }
    }

    private static Map<String, String> slotArchetypeIdsByLocalNodeId(OperationalTemplate templateOpt) {
        Map<String, String> slots = new HashMap<>();
        if (templateOpt.getDefinition() == null) {
            return slots;
        }
        collectSlots(templateOpt.getDefinition(), slots);
        return slots;
    }

    private static void collectSlots(CObject object, Map<String, String> slots) {
        if (object instanceof ArchetypeSlot slot) {
            String resolved = ArchieOptParser.resolveSlotArchetypeNodeId(slot);
            if (resolved != null && slot.getNodeId() != null) {
                slots.put(slot.getNodeId(), resolved);
            }
        }
        if (object instanceof CComplexObject complexObject && complexObject.getAttributes() != null) {
            for (CAttribute attribute : complexObject.getAttributes()) {
                if (attribute.getChildren() == null) {
                    continue;
                }
                for (CObject child : attribute.getChildren()) {
                    collectSlots(child, slots);
                }
            }
        }
    }
}
