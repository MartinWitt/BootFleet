package io.github.martinwitt.sequentialthinkingmcp.mcp.dto;

/** Result of adding a thought to the thinking chain. */
public record AddThoughtResult(
        String sessionId,
        int thoughtNumber,
        int totalThoughts,
        boolean nextThoughtNeeded,
        int thoughtCount,
        int branchCount) {}
