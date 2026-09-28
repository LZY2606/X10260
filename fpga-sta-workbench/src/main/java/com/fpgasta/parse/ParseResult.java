package com.fpgasta.parse;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/** Output of {@link StaDslParser}: parsed entity records and precise diagnostics. */
public final class ParseResult {

    private final List<ObjectNode> tools = new ArrayList<>();
    private final List<ObjectNode> clocks = new ArrayList<>();
    private final List<ObjectNode> exceptions = new ArrayList<>();
    private final List<ObjectNode> paths = new ArrayList<>();
    private final List<ObjectNode> relations = new ArrayList<>();
    private final List<ObjectNode> diagnostics = new ArrayList<>();

    public List<ObjectNode> tools() {
        return tools;
    }

    public List<ObjectNode> clocks() {
        return clocks;
    }

    public List<ObjectNode> exceptions() {
        return exceptions;
    }

    public List<ObjectNode> paths() {
        return paths;
    }

    public List<ObjectNode> relations() {
        return relations;
    }

    public List<ObjectNode> diagnostics() {
        return diagnostics;
    }

    public boolean hasErrors() {
        for (ObjectNode diagnostic : diagnostics) {
            if ("error".equals(diagnostic.path("severity").asText())) {
                return true;
            }
        }
        return false;
    }
}
