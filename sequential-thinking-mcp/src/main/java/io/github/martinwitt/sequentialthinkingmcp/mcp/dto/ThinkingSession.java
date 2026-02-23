package io.github.martinwitt.sequentialthinkingmcp.mcp.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Represents a complete thinking session with all thoughts, branches, and state. */
public class ThinkingSession {

    private final String sessionId;
    private int currentThoughtNumber;
    private int totalThoughtsEstimate;
    private final List<Thought> thoughts;
    private final Map<String, ThoughtBranch> branches;
    private final Instant createdAt;
    private Instant lastAccessedAt;
    private SessionStatus status;
    private String finalAnswer;
    private String hypothesisText;
    private Instant hypothesisTimestamp;
    private final List<String> verificationSteps;

    public ThinkingSession(String sessionId, int initialEstimate) {
        this.sessionId = sessionId;
        this.currentThoughtNumber = 0;
        this.totalThoughtsEstimate = initialEstimate;
        this.thoughts = new ArrayList<>();
        this.branches = new HashMap<>();
        this.createdAt = Instant.now();
        this.lastAccessedAt = Instant.now();
        this.status = SessionStatus.ACTIVE;
        this.finalAnswer = "";
        this.verificationSteps = new ArrayList<>();
    }

    // Getters and setters
    public String getSessionId() {
        return sessionId;
    }

    public int getCurrentThoughtNumber() {
        return currentThoughtNumber;
    }

    public void setCurrentThoughtNumber(int currentThoughtNumber) {
        this.currentThoughtNumber = currentThoughtNumber;
    }

    public int getTotalThoughtsEstimate() {
        return totalThoughtsEstimate;
    }

    public void setTotalThoughtsEstimate(int totalThoughtsEstimate) {
        this.totalThoughtsEstimate = totalThoughtsEstimate;
    }

    public List<Thought> getThoughts() {
        return thoughts;
    }

    public void addThought(Thought thought) {
        this.thoughts.add(thought);
        updateLastAccessed();
    }

    public Map<String, ThoughtBranch> getBranches() {
        return branches;
    }

    public void addBranch(ThoughtBranch branch) {
        this.branches.put(branch.branchId(), branch);
        updateLastAccessed();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void updateLastAccessed() {
        this.lastAccessedAt = Instant.now();
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public String getHypothesisText() {
        return hypothesisText;
    }

    public Instant getHypothesisTimestamp() {
        return hypothesisTimestamp;
    }

    public void setHypothesis(String text, Instant timestamp) {
        this.hypothesisText = text;
        this.hypothesisTimestamp = timestamp;
    }

    public List<String> getVerificationSteps() {
        return verificationSteps;
    }

    public void addVerificationStep(String step) {
        this.verificationSteps.add(step);
        updateLastAccessed();
    }

    public int getThoughtCount() {
        return thoughts.size();
    }

    public int getBranchCount() {
        return branches.size();
    }
}
