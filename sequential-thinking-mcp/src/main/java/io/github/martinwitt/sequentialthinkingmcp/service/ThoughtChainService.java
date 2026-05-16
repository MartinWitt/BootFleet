package io.github.martinwitt.sequentialthinkingmcp.service;

import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.SessionStatus;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.ThinkingSession;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.Thought;
import io.github.martinwitt.sequentialthinkingmcp.mcp.dto.ThoughtBranch;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ThoughtChainService {

    private static final Logger logger = LoggerFactory.getLogger(ThoughtChainService.class);
    private static final String SESSION_NOT_FOUND = "Session not found: ";
    private static final String THOUGHT_NOT_FOUND = "Thought to revise not found: ";
    private static final String BRANCH_NOT_FOUND = "Branch not found: ";
    private static final String MAX_BRANCHES_REACHED = "Maximum branches per session reached: ";
    private static final String MAX_THOUGHTS_REACHED = "Maximum thoughts per session reached: ";

    private final Map<String, ThinkingSession> sessions = new ConcurrentHashMap<>();

    @Value("${app.thinking.session.timeout-minutes:30}")
    private long sessionTimeoutMinutes;

    @Value("${app.thinking.thought.max-per-session:1000}")
    private int maxThoughtsPerSession;

    @Value("${app.thinking.branch.max-per-session:100}")
    private int maxBranchesPerSession;

    public ThinkingSession startThinkingSession(int initialThoughtEstimate) {
        String sessionId = UUID.randomUUID().toString();
        ThinkingSession session = new ThinkingSession(sessionId, initialThoughtEstimate);
        sessions.put(sessionId, session);
        logger.info(
                "Started new thinking session {} with initial estimate of {} thoughts",
                sessionId,
                initialThoughtEstimate);
        return session;
    }

    public ThinkingSession getSession(String sessionId) {
        ThinkingSession session = sessions.get(sessionId);
        if (session != null) {
            session.updateLastAccessed();
        }
        return session;
    }

    public Thought addThought(String sessionId, String thoughtContent, boolean nextThoughtNeeded) {
        ThinkingSession session = validateSessionExists(sessionId);

        if (session.getThoughtCount() >= maxThoughtsPerSession) {
            throw new IllegalStateException(MAX_THOUGHTS_REACHED + maxThoughtsPerSession);
        }

        int thoughtNumber = session.getCurrentThoughtNumber() + 1;
        session.setCurrentThoughtNumber(thoughtNumber);

        String thoughtId = UUID.randomUUID().toString();
        Thought thought =
                new Thought(
                        thoughtId, thoughtNumber, thoughtContent, false, null, false, null, null);

        session.addThought(thought);
        logger.debug(
                "Added thought {} (number {}) to session {}", thoughtId, thoughtNumber, sessionId);
        return thought;
    }

    public Thought reviseThought(
            String sessionId, String thoughtContent, int revisesThoughtNumber) {
        ThinkingSession session = validateSessionExists(sessionId);
        validateThoughtExists(session, revisesThoughtNumber);

        int newThoughtNumber = session.getCurrentThoughtNumber() + 1;
        session.setCurrentThoughtNumber(newThoughtNumber);

        String thoughtId = UUID.randomUUID().toString();
        Thought thought =
                new Thought(
                        thoughtId,
                        newThoughtNumber,
                        thoughtContent,
                        true,
                        revisesThoughtNumber,
                        false,
                        null,
                        null);

        session.addThought(thought);
        logger.debug(
                "Added revision thought {} (number {}) revising thought {} in session {}",
                thoughtId,
                newThoughtNumber,
                revisesThoughtNumber,
                sessionId);
        return thought;
    }

    public ThoughtBranch branchThought(
            String sessionId, int branchFromThoughtNumber, String branchDescription) {
        ThinkingSession session = validateSessionExists(sessionId);
        validateThoughtExists(session, branchFromThoughtNumber);

        if (session.getBranchCount() >= maxBranchesPerSession) {
            throw new IllegalStateException(MAX_BRANCHES_REACHED + maxBranchesPerSession);
        }

        String branchId = "branch_" + UUID.randomUUID();
        ThoughtBranch branch =
                new ThoughtBranch(branchId, branchFromThoughtNumber, branchDescription);
        session.addBranch(branch);
        logger.debug(
                "Created branch {} from thought {} in session {}",
                branchId,
                branchFromThoughtNumber,
                sessionId);
        return branch;
    }

    public Thought addThoughtToBranch(String sessionId, String branchId, String thoughtContent) {
        ThinkingSession session = validateSessionExists(sessionId);

        ThoughtBranch branch = session.getBranches().get(branchId);
        if (branch == null) {
            throw new IllegalArgumentException(BRANCH_NOT_FOUND + branchId);
        }

        int thoughtNumber = session.getCurrentThoughtNumber() + 1;
        session.setCurrentThoughtNumber(thoughtNumber);

        String thoughtId = UUID.randomUUID().toString();
        Thought thought =
                new Thought(
                        thoughtId,
                        thoughtNumber,
                        thoughtContent,
                        false,
                        null,
                        true,
                        branch.branchFromThought(),
                        branchId);

        session.addThought(thought);

        List<String> thoughtIds =
                Stream.concat(branch.thoughtIds().stream(), Stream.of(thoughtId)).toList();
        ThoughtBranch updatedBranch =
                new ThoughtBranch(
                        branchId,
                        branch.branchFromThought(),
                        thoughtIds,
                        branch.description(),
                        branch.status());
        session.getBranches().put(branchId, updatedBranch);

        logger.debug(
                "Added thought {} (number {}) to branch {} in session {}",
                thoughtId,
                thoughtNumber,
                branchId,
                sessionId);
        return thought;
    }

    public ThinkingSession completeThinkingSession(String sessionId, String finalAnswer) {
        ThinkingSession session = validateSessionExists(sessionId);
        session.setFinalAnswer(finalAnswer);
        session.setStatus(SessionStatus.COMPLETED);
        logger.info("Completed thinking session {} with answer: {}", sessionId, finalAnswer);
        return session;
    }

    public ThinkingSession pauseSession(String sessionId) {
        ThinkingSession session = validateSessionExists(sessionId);
        session.setStatus(SessionStatus.PAUSED);
        logger.info("Paused thinking session {}", sessionId);
        return session;
    }

    public ThinkingSession resumeSession(String sessionId) {
        ThinkingSession session = validateSessionExists(sessionId);
        session.setStatus(SessionStatus.ACTIVE);
        session.updateLastAccessed();
        logger.info("Resumed thinking session {}", sessionId);
        return session;
    }

    @Scheduled(fixedRateString = "${app.thinking.session.cleanup-interval-minutes:5}m")
    public void cleanupIdleSessions() {
        Instant cutoff = Instant.now().minusSeconds(sessionTimeoutMinutes * 60);

        List<String> expiredIds =
                sessions.entrySet().stream()
                        .filter(e -> e.getValue().getLastAccessedAt().isBefore(cutoff))
                        .map(Map.Entry::getKey)
                        .toList();

        expiredIds.forEach(
                id -> {
                    sessions.remove(id);
                    logger.info("Cleaned up idle session {}", id);
                });

        logger.debug(
                "Session cleanup complete. Removed {} idle sessions. Active sessions: {}",
                expiredIds.size(),
                sessions.size());
    }

    public Map<String, ThinkingSession> getAllSessions() {
        return new ConcurrentHashMap<>(sessions);
    }

    public int getSessionCount() {
        return sessions.size();
    }

    private ThinkingSession validateSessionExists(String sessionId) {
        ThinkingSession session = getSession(sessionId);
        if (session == null) {
            throw new IllegalArgumentException(SESSION_NOT_FOUND + sessionId);
        }
        return session;
    }

    private void validateThoughtExists(ThinkingSession session, int thoughtNumber) {
        boolean found =
                session.getThoughts().stream().anyMatch(t -> t.thoughtNumber() == thoughtNumber);
        if (!found) {
            throw new IllegalArgumentException(THOUGHT_NOT_FOUND + thoughtNumber);
        }
    }
}
