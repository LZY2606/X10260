package com.fpgasta.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Shared Jackson configuration. Ordering of object fields follows insertion order. */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper();
    public static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.instance;

    private Json() {
    }

    public static ObjectNode object() {
        return NODE_FACTORY.objectNode();
    }

    public static String canonical(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialize value", e);
        }
    }
}
