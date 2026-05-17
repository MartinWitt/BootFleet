package io.github.martinwitt.sequentialthinkingmcp.mcp;

import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.AddThoughtResult;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.BranchThoughtResult;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.CompleteThinkingResult;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.GetThoughtChainResult;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.HypothesisStatus;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.ReviseThoughtResult;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.StartThinkingResult;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.ThinkingSession;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.Thought;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.ThoughtBranch;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.VerifyHypothesisResult;
import io.github.martinwitt.sequentialthinkingmcp.service.ThoughtChainService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP Tools for Sequential Thinking with dynamic and reflective problem-solving.
 *
 * <p>Exposes callable tools for initiating thinking sessions, adding/revising/branching thoughts,
 * verifying hypotheses, and completing problem-solving processes using Spring AI MCP annotations.
 *
 * <p>This tool maintains stateful thinking sessions that support:
 *
 * <ul>
 *   <li>Sequential thought chains with revision support
 *   <li>Branching for exploring alternative approaches
 *   <li>Hypothesis generation and verification
 *   <li>Dynamic thought counting with adaptable estimates
 * </ul>
 */
@Component
public class SequentialThinkingMcpTools {

    private static final Logger logger = LoggerFactory.getLogger(SequentialThinkingMcpTools.class);

    private static final String SESSION_NOT_FOUND_MSG = "Session not found: ";
    private static final double CONFIDENCE_BASE = 0.5;
    private static final double CONFIDENCE_INCREMENT_PER_STEP = 0.1;
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.7;
    private static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.4;
    private static final double MAX_CONFIDENCE = 1.0;
    private static final int MIN_THOUGHTS_ESTIMATE = 1;

    private final ThoughtChainService thoughtChainService;

    public SequentialThinkingMcpTools(ThoughtChainService thoughtChainService) {
        this.thoughtChainService = thoughtChainService;
    }

    @McpTool(
            name = "sequentialthinking",
            description =
                    """
                    Add a single step to an active sequential thinking session. This is the\
                     core tool in the thinking workflow: call sequentialthinking-start first\
                     to get a sessionId, then call this tool repeatedly for each reasoning\
                     step, and finish with sequentialthinking-complete. Use this over native\
                     LLM reasoning when you need a persistent, reviewable, and revisable\
                     thought chain — for example for multi-step debugging, architectural\
                     decisions, or any problem where you might need to backtrack and revise\
                     earlier steps. Set nextThoughtNeeded=false on the final step.\
                    """)
    public AddThoughtResult addThought(
            @McpToolParam(
                            description =
                                    """
                                    Your current thinking step, which can include: regular
                                     analytical steps, revisions of previous thoughts,
                                     questions about previous decisions, realizations about
                                     needing more analysis, changes in approach, hypothesis
                                     generation, or hypothesis verification\
                                    """,
                            required = true)
                    String thought,
            @McpToolParam(description = "Whether another thought step is needed", required = true)
                    boolean nextThoughtNeeded,
            @McpToolParam(
                            description =
                                    """
                                    Current thought number in sequence (numeric value, e.g., 1, 2,
                                     3)\
                                    """,
                            required = true)
                    int thoughtNumber,
            @McpToolParam(
                            description =
                                    "Current estimate of total thoughts needed (can be adjusted"
                                            + " up/down)",
                            required = true)
                    int totalThoughts,
            @McpToolParam(
                            description =
                                    "Session ID for this thinking chain (use empty string to create"
                                            + " new session)",
                            required = false)
                    String sessionId,
            @McpToolParam(
                            description = "Whether this thought revises previous thinking",
                            required = false)
                    Boolean isRevision,
            @McpToolParam(
                            description = "If revising, which thought number is being reconsidered",
                            required = false)
                    Integer revisesThought,
            @McpToolParam(
                            description =
                                    "If branching, which thought number is the branching point",
                            required = false)
                    Integer branchFromThought,
            @McpToolParam(
                            description = "Branch identifier for the current branch (if any)",
                            required = false)
                    String branchId) {

        logger.info(
                "Received thought #{} (total estimate: {}), nextNeeded={}, sessionId={}",
                thoughtNumber,
                totalThoughts,
                nextThoughtNeeded,
                sessionId);

        // Create new session if needed
        String effectiveSessionId = getOrCreateSession(sessionId, totalThoughts);

        ThinkingSession session = thoughtChainService.getSession(effectiveSessionId);
        if (session == null) {
            throw new IllegalArgumentException(SESSION_NOT_FOUND_MSG + effectiveSessionId);
        }

        processThought(
                effectiveSessionId,
                thought,
                nextThoughtNeeded,
                isRevision,
                revisesThought,
                branchFromThought,
                branchId);

        // Update thought count estimate if provided
        if (totalThoughts != session.getTotalThoughtsEstimate()) {
            session.setTotalThoughtsEstimate(totalThoughts);
            logger.info(
                    "Updated thought estimate for session {} to {}",
                    effectiveSessionId,
                    totalThoughts);
        }

        return new AddThoughtResult(
                effectiveSessionId,
                session.getCurrentThoughtNumber(),
                session.getTotalThoughtsEstimate(),
                nextThoughtNeeded,
                session.getThoughtCount(),
                session.getBranchCount());
    }

    private String getOrCreateSession(String sessionId, int totalThoughts) {
        if (sessionId == null || sessionId.isEmpty()) {
            ThinkingSession newSession = thoughtChainService.startThinkingSession(totalThoughts);
            String newSessionId = newSession.getSessionId();
            logger.info("Created new thinking session: {}", newSessionId);
            return newSessionId;
        }
        return sessionId;
    }

    private void processThought(
            String sessionId,
            String thought,
            boolean nextThoughtNeeded,
            Boolean isRevision,
            Integer revisesThought,
            Integer branchFromThought,
            String branchId) {
        // Handle revisions
        if (isRevision != null && isRevision && revisesThought != null) {
            thoughtChainService.reviseThought(sessionId, thought, revisesThought);
        }
        // Handle branching
        else if (branchFromThought != null) {
            handleBranching(sessionId, thought, branchFromThought, branchId);
        }
        // Regular thought
        else {
            thoughtChainService.addThought(sessionId, thought, nextThoughtNeeded);
        }
    }

    private void handleBranching(
            String sessionId, String thought, int branchFrom, String branchId) {
        String effectiveBranchId = branchId;
        // Create branch if needed
        if (branchId == null || branchId.isEmpty()) {
            ThoughtBranch branch =
                    thoughtChainService.branchThought(
                            sessionId, branchFrom, "Branch from thought " + branchFrom);
            effectiveBranchId = branch.branchId();
        }
        thoughtChainService.addThoughtToBranch(sessionId, effectiveBranchId, thought);
    }

    /**
     * Gets a session or throws IllegalArgumentException if not found.
     *
     * @param sessionId The session ID
     * @return The session
     * @throws IllegalArgumentException if session not found
     */
    private ThinkingSession getSessionOrThrow(String sessionId) {
        ThinkingSession session = thoughtChainService.getSession(sessionId);
        if (session == null) {
            throw new IllegalArgumentException(SESSION_NOT_FOUND_MSG + sessionId);
        }
        return session;
    }

    @McpTool(
            name = "sequentialthinking-start",
            description =
                    "Start a new sequential thinking session and get a sessionId. Always call this"
                            + " first before using any other sequentialthinking-* tools. The"
                            + " returned sessionId links all subsequent thoughts, revisions, and"
                            + " branches together. Provide an initial estimate of how many steps"
                            + " the problem will take — this can be adjusted freely during the"
                            + " session.")
    public StartThinkingResult startThinking(
            @McpToolParam(
                            description =
                                    "Initial estimate of total thoughts needed for this problem"
                                            + " (can be adjusted later)",
                            required = true)
                    int initialThoughtEstimate) {

        logger.info(
                "Starting new thinking session with initial estimate of {} thoughts",
                initialThoughtEstimate);
        ThinkingSession session = thoughtChainService.startThinkingSession(initialThoughtEstimate);
        return new StartThinkingResult(
                session.getSessionId(),
                session.getThoughtCount(),
                session.getTotalThoughtsEstimate(),
                session.getStatus());
    }

    @McpTool(
            name = "sequentialthinking-revise",
            description =
                    """
                    Go back and correct an earlier thought in an active session. Use this when\
                     you realize a previous reasoning step was wrong or incomplete — it creates\
                     a revision record that keeps the full history intact rather than silently\
                     overwriting. Call this instead of sequentialthinking when the new thought\
                     explicitly supersedes an earlier one. The session continues normally after\
                     a revision.\
                    """)
    public ReviseThoughtResult reviseThought(
            @McpToolParam(description = "The thinking session ID", required = true)
                    String sessionId,
            @McpToolParam(description = "Your revised thinking", required = true) String thought,
            @McpToolParam(description = "The thought number being revised", required = true)
                    int revisesThoughtNumber) {

        logger.info(
                "Revising thought {} in session {} with new content",
                revisesThoughtNumber,
                sessionId);

        ThinkingSession session = getSessionOrThrow(sessionId);
        Thought revised =
                thoughtChainService.reviseThought(sessionId, thought, revisesThoughtNumber);

        // Find all thoughts affected by this revision (thoughts after the revised one)
        List<Integer> affectedThoughts =
                session.getThoughts().stream()
                        .filter(t -> t.thoughtNumber() > revisesThoughtNumber)
                        .map(Thought::thoughtNumber)
                        .toList();

        return new ReviseThoughtResult(
                sessionId,
                revisesThoughtNumber,
                revised.thoughtNumber(),
                true,
                affectedThoughts,
                session.getTotalThoughtsEstimate());
    }

    @McpTool(
            name = "sequentialthinking-branch",
            description =
                    "Fork the thought chain at a specific step to explore an alternative approach"
                            + " in parallel. Use this when there are two or more plausible solution"
                            + " paths and you want to reason through each independently — for"
                            + " example comparing two architectural options or two root-cause"
                            + " hypotheses. Returns a branchId to pass into subsequent"
                            + " sequentialthinking calls for that branch.")
    public BranchThoughtResult branchThought(
            @McpToolParam(description = "The thinking session ID", required = true)
                    String sessionId,
            @McpToolParam(description = "The thought number to branch from", required = true)
                    int branchFromThoughtNumber,
            @McpToolParam(description = "Description of what this branch explores", required = true)
                    String branchDescription) {

        logger.info(
                "Creating branch from thought {} in session {}",
                branchFromThoughtNumber,
                sessionId);

        ThinkingSession session = getSessionOrThrow(sessionId);
        ThoughtBranch branch =
                thoughtChainService.branchThought(
                        sessionId, branchFromThoughtNumber, branchDescription);

        List<String> allBranches = session.getBranches().keySet().stream().toList();

        return new BranchThoughtResult(
                sessionId,
                branch.branchId(),
                branchFromThoughtNumber,
                session.getCurrentThoughtNumber() + 1,
                allBranches,
                session.getBranchCount());
    }

    @McpTool(
            name = "sequentialthinking-verify",
            description =
                    "Validate a proposed conclusion against the thought chain before committing to"
                            + " it. Call this after you have added all reasoning steps but before"
                            + " sequentialthinking-complete, when you want an explicit confidence"
                            + " score based on how many verification steps support the hypothesis."
                            + " Provide a list of concrete checks that confirm or refute the"
                            + " hypothesis — more steps produce higher confidence scores.")
    public VerifyHypothesisResult verifyHypothesis(
            @McpToolParam(description = "The thinking session ID", required = true)
                    String sessionId,
            @McpToolParam(description = "The hypothesis to verify", required = true)
                    String hypothesis,
            @McpToolParam(
                            description =
                                    "Verification steps taken to confirm or reject the hypothesis",
                            required = true)
                    List<String> verificationSteps) {

        logger.info("Verifying hypothesis in session {}", sessionId);

        ThinkingSession session = getSessionOrThrow(sessionId);

        // Store hypothesis and verification steps
        session.setHypothesis(hypothesis, Instant.now());
        verificationSteps.forEach(session::addVerificationStep);

        // Calculate confidence based on verification steps
        double confidence =
                Math.min(
                        MAX_CONFIDENCE,
                        CONFIDENCE_BASE
                                + (verificationSteps.size() * CONFIDENCE_INCREMENT_PER_STEP));
        HypothesisStatus status = getConfidenceStatus(confidence);

        return new VerifyHypothesisResult(
                sessionId,
                status,
                verificationSteps,
                confidence,
                "Hypothesis verification in progress");
    }

    private HypothesisStatus getConfidenceStatus(double confidence) {
        if (confidence > HIGH_CONFIDENCE_THRESHOLD) {
            return HypothesisStatus.VERIFIED;
        } else if (confidence > MEDIUM_CONFIDENCE_THRESHOLD) {
            return HypothesisStatus.TESTING;
        } else {
            return HypothesisStatus.PROPOSED;
        }
    }

    @McpTool(
            name = "sequentialthinking-get-chain",
            description =
                    "Inspect the current state of a thinking session mid-process. Returns thought"
                            + " count, branch count, and session status without modifying anything."
                            + " Use this to get an overview before deciding whether to add more"
                            + " thoughts, create a branch, or call sequentialthinking-complete."
                            + " Also useful for debugging a session if the flow becomes unclear.")
    public GetThoughtChainResult getThoughtChain(
            @McpToolParam(description = "The thinking session ID", required = true)
                    String sessionId) {

        logger.info("Retrieving thought chain for session {}", sessionId);

        ThinkingSession session = getSessionOrThrow(sessionId);

        List<String> branches = session.getBranches().keySet().stream().toList();

        return new GetThoughtChainResult(
                sessionId,
                session.getThoughtCount(),
                session.getTotalThoughtsEstimate(),
                branches,
                session.getThoughts().size(),
                session.getStatus());
    }

    @McpTool(
            name = "sequentialthinking-complete",
            description =
                    "Close the thinking session with a final answer and get session statistics."
                            + " This is always the last call in the workflow"
                            + " (sequentialthinking-start → sequentialthinking → this)."
                            + " Call this once you have finished all reasoning steps and optionally"
                            + " verified your hypothesis. After this call the session is closed"
                            + " and no more thoughts can be added.")
    public CompleteThinkingResult completeThinking(
            @McpToolParam(description = "The thinking session ID", required = true)
                    String sessionId,
            @McpToolParam(description = "The final answer or conclusion", required = true)
                    String finalAnswer) {

        logger.info("Completing thinking session {}", sessionId);

        ThinkingSession completed =
                thoughtChainService.completeThinkingSession(sessionId, finalAnswer);
        long durationSeconds = ChronoUnit.SECONDS.between(completed.getCreatedAt(), Instant.now());

        return new CompleteThinkingResult(
                sessionId,
                finalAnswer,
                completed.getThoughtCount(),
                completed.getBranchCount(),
                completed.getStatus(),
                durationSeconds);
    }
}
