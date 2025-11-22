package io.github.martinwitt.mailsummary.model;

import java.util.List;

public record EmailSummary(String from, String subject, String summary, List<String> actionItems) {}
