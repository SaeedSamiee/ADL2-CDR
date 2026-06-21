/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.adl2.knowledge;

public class Adl2KnowledgeException extends RuntimeException {

    public Adl2KnowledgeException(String message) {
        super(message);
    }

    public Adl2KnowledgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
