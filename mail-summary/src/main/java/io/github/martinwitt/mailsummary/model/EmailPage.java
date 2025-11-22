package io.github.martinwitt.mailsummary.model;

import java.util.List;

public record EmailPage(List<EmailMessage> messages, String nextPageToken) {}
