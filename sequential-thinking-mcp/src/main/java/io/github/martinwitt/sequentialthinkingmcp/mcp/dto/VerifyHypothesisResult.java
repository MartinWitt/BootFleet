package io.github.martinwitt.sequentialthinkingmcp.mcp.dto;

import java.util.List;

/** Result of verifying a hypothesis. */
public record VerifyHypothesisResult(
        String sessionId,
        HypothesisStatus hypothesisStatus,
        List<String> verificationSteps,
        double confidenceScore,
        String conclusion) {}
