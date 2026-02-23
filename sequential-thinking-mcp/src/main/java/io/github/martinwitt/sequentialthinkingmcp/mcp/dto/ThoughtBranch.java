package io.github.martinwitt.sequentialthinkingmcp.mcp.dto;

import java.util.List;

/** Represents a branch in the thinking process. */
public record ThoughtBranch(
        String branchId,
        int branchFromThought,
        List<String> thoughtIds,
        String description,
        String status) {

    public ThoughtBranch(String branchId, int branchFromThought, String description) {
        this(branchId, branchFromThought, List.of(), description, "CREATED");
    }
}
