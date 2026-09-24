package workbench.model;

import com.fasterxml.jackson.databind.JsonNode;

public record Event(long seq, String eventId, String parentEventId, String type, String virtualTime,
                    String actor, JsonNode payload, String inputHash, String previousHash) {}
