/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.api.knowledge;

public enum TemplateFormat {
    ADL14("adl1.4"),
    ADL2("adl2");

    private final String wireValue;

    TemplateFormat(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static TemplateFormat fromWire(String value) {
        if (value == null || value.isBlank()) {
            return ADL14;
        }
        for (TemplateFormat format : values()) {
            if (format.wireValue.equalsIgnoreCase(value) || format.name().equalsIgnoreCase(value)) {
                return format;
            }
        }
        return ADL14;
    }
}
