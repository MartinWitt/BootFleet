package io.github.martinwitt.servicefinder;

import java.util.List;

public record ServiceEntry(String name, String namespace, List<String> urls) {}
