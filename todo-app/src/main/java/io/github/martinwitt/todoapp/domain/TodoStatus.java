package io.github.martinwitt.todoapp.domain;

public enum TodoStatus {
    OPEN,
    IN_PROGRESS,
    DONE;

    @Override
    public String toString() {
        String[] words = name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
