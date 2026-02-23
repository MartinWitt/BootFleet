package io.github.martinwitt.sequentialthinkingmcp.mcp.dto;

import java.util.List;

/** Result of branching from a thought. */
public record BranchThoughtResult(
        String sessionId,
        String branchId,
        int branchFromThought,
        int initialThoughtNumber,
        List<String> branches,
        int totalBranches) {}
