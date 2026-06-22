/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

/** Failed ADL rule assertion detected during composition validation. */
public record Adl2RuleViolation(String assertionTag, String message, String templateId) {}
