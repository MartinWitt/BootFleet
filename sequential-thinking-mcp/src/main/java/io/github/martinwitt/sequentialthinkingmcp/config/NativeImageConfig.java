package io.github.martinwitt.sequentialthinkingmcp.config;

import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.AddThoughtResult;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.BranchThoughtResult;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.CompleteThinkingResult;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.GetThoughtChainResult;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.ReviseThoughtResult;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.StartThinkingResult;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.ThinkingSession;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.Thought;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.ThoughtBranch;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.VerifyHypothesisResult;
import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Configuration;

// Workaround for https://github.com/spring-projects/spring-ai/pull/6293
@Configuration
@RegisterReflectionForBinding({
    AddThoughtResult.class,
    BranchThoughtResult.class,
    CompleteThinkingResult.class,
    DefaultMetaProvider.class,
    GetThoughtChainResult.class,
    ReviseThoughtResult.class,
    StartThinkingResult.class,
    Thought.class,
    ThinkingSession.class,
    ThoughtBranch.class,
    VerifyHypothesisResult.class
})
public class NativeImageConfig {}
