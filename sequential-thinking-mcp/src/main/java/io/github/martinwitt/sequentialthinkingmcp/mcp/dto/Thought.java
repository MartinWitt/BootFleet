package io.github.martinwitt.sequentialthinkingmcp.mcp.dto;

import java.time.Instant;
import java.util.List;

/** Represents a single thought in the thinking chain. */
public record Thought(
        String id,
        int thoughtNumber,
        String content,
        boolean isRevision,
        Integer revisesThought,
        boolean isBranch,
        Integer branchFromThought,
        String branchId,
        Instant createdAt,
        List<String> tags,
        String status) {

    public Thought(
            String id,
            int thoughtNumber,
            String content,
            boolean isRevision,
            Integer revisesThought,
            boolean isBranch,
            Integer branchFromThought,
            String branchId) {
        this(
                id,
                thoughtNumber,
                content,
                isRevision,
                revisesThought,
                isBranch,
                branchFromThought,
                branchId,
                Instant.now(),
                List.of(),
                "CREATED");
    }
}
