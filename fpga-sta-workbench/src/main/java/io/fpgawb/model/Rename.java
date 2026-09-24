package io.fpgawb.model;

/**
 * A netlist object rename event. Provenance keeps both names and the affected path endpoint
 * so identity can survive a localised source edit.
 */
public record Rename(String id, String oldName, String newName, String reason, String text) {
}
