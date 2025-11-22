package io.github.martinwitt.mailsummary.model;

public record EmailMessage(String id, String from, String subject, String body) {}
