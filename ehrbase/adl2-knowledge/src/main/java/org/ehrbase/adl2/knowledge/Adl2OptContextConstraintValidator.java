/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

import com.nedap.archie.aom.CAttribute;
import com.nedap.archie.aom.CComplexObject;
import com.nedap.archie.aom.CObject;
import com.nedap.archie.aom.primitives.CString;
import com.nedap.archie.aom.primitives.COrdered;
import com.nedap.archie.aom.OperationalTemplate;
import com.nedap.archie.base.Interval;
import com.nedap.archie.query.RMObjectWithPath;
import com.nedap.archie.query.RMPathQuery;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import com.nedap.archie.rminfo.ArchieRMInfoLookup;
import com.nedap.archie.rmobjectvalidator.RMObjectValidationMessage;
import com.nedap.archie.rmobjectvalidator.RMObjectValidationMessageType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Validates ADL primitive constraints under /context using RM path queries. */
final class Adl2OptContextConstraintValidator {

    private Adl2OptContextConstraintValidator() {}

    static List<RMObjectValidationMessage> validate(Composition composition, OperationalTemplate opt) {
        List<RMObjectValidationMessage> messages = new ArrayList<>();
        if (opt.getDefinition() == null) {
            return messages;
        }
        walk(opt.getDefinition(), "", messages, composition);
        return messages;
    }

    private static void walk(CObject object, String path, List<RMObjectValidationMessage> messages, Composition composition) {
        if (object instanceof CString cString && path.startsWith("/context") && !cString.getConstraint().isEmpty()) {
            validateStringConstraint(toDataPath(path), cString, messages, composition);
        }
        if (object instanceof COrdered<?> cOrdered && path.startsWith("/context") && !cOrdered.getConstraint().isEmpty()) {
            validateQuantityConstraint(path, cOrdered, messages, composition);
        }
        if (object instanceof CComplexObject complexObject && complexObject.getAttributes() != null) {
            for (CAttribute attribute : complexObject.getAttributes()) {
                if (attribute.getChildren() == null) {
                    continue;
                }
                for (CObject child : attribute.getChildren()) {
                    walk(child, appendPath(path, attribute.getRmAttributeName(), child), messages, composition);
                }
            }
        }
    }

    private static void validateStringConstraint(
            String rmPath, CString cString, List<RMObjectValidationMessage> messages, Composition composition) {
        if (rmPath.endsWith("/units") || rmPath.endsWith("/magnitude")) {
            return;
        }
        for (RMObjectWithPath match :
                new RMPathQuery(rmPath).findList(ArchieRMInfoLookup.getInstance(), composition)) {
            String value = stringValue(match.getObject());
            if (value != null && !isValidString(cString, value)) {
                messages.add(violation(rmPath, "The value \"" + value + "\" is not allowed"));
            }
        }
    }

    private static void validateQuantityConstraint(
            String aomPath, COrdered<?> cOrdered, List<RMObjectValidationMessage> messages, Composition composition) {
        String quantityPath = quantityDataPath(aomPath);
        for (RMObjectWithPath match :
                new RMPathQuery(quantityPath).findList(ArchieRMInfoLookup.getInstance(), composition)) {
            if (match.getObject() instanceof DvQuantity quantity
                    && quantity.getMagnitude() != null
                    && !isValidQuantity(cOrdered, quantity)) {
                messages.add(violation(quantityPath + "/magnitude", "Magnitude " + quantity.getMagnitude()
                        + " is out of allowed range"));
            }
        }
    }

    private static RMObjectValidationMessage violation(String path, String message) {
        return new RMObjectValidationMessage(path, null, null, null, message, RMObjectValidationMessageType.DEFAULT);
    }

    /** Map AOM constraint paths to RM instance paths for data value leaves. */
    private static String toDataPath(String path) {
        String rmPath = path;
        if (rmPath.endsWith("/value/value")) {
            rmPath = rmPath.substring(0, rmPath.length() - "/value".length());
        }
        rmPath = rmPath.replaceAll("/value\\[[^]]+]$", "/value");
        if (rmPath.endsWith("/value/value")) {
            rmPath = rmPath.substring(0, rmPath.length() - "/value".length());
        }
        return rmPath;
    }

    private static String quantityDataPath(String aomPath) {
        String rmPath = aomPath;
        if (rmPath.endsWith("/magnitude")) {
            rmPath = rmPath.substring(0, rmPath.length() - "/magnitude".length());
        }
        return toDataPath(rmPath);
    }

    private static String stringValue(Object object) {
        if (object instanceof DvText dvText) {
            return dvText.getValue();
        }
        if (object instanceof String string) {
            return string;
        }
        return null;
    }

    private static boolean isValidString(CString cString, String value) {
        for (String constraint : cString.getConstraint()) {
            if (Objects.equals(value, constraint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidQuantity(COrdered<?> cOrdered, DvQuantity quantity) {
        Double magnitude = quantity.getMagnitude();
        String units = quantity.getUnits();
        if (magnitude == null || units == null) {
            return false;
        }
        for (Object constraint : cOrdered.getConstraint()) {
            if (constraint instanceof Interval<?> interval && magnitudeIntervalHasDouble(interval, magnitude)) {
                return true;
            }
            if (!(constraint instanceof List<?> tuple) || tuple.size() < 2) {
                continue;
            }
            Object unitsConstraint = tuple.get(0);
            Object magnitudeConstraint = tuple.get(1);
            if (unitsConstraint instanceof List<?> allowedUnits
                    && allowedUnits.stream()
                            .map(Object::toString)
                            .map(Adl2OptContextConstraintValidator::stripQuotes)
                            .noneMatch(units::equals)) {
                continue;
            }
            if (magnitudeConstraint instanceof Interval<?> magnitudeInterval
                    && magnitudeIntervalHasDouble(magnitudeInterval, magnitude)) {
                return true;
            }
        }
        return false;
    }

    private static boolean magnitudeIntervalHasDouble(Interval<?> interval, Double magnitude) {
        if (interval.getLower() instanceof Number lower && magnitude < lower.doubleValue()) {
            return false;
        }
        if (interval.getUpper() instanceof Number upper && magnitude >= upper.doubleValue()) {
            return false;
        }
        return true;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String appendPath(String prefix, String rmAttributeName, CObject child) {
        String nodeId = child instanceof CComplexObject co ? co.getNodeId() : null;
        if (nodeId != null && !nodeId.isBlank()) {
            return prefix + "/" + rmAttributeName + "[" + nodeId + "]";
        }
        return prefix + "/" + rmAttributeName;
    }
}
