package io.github.varnitta.plugkit.db.exception;

import java.util.List;
import java.util.stream.Collectors;

public final class InvalidConfigException extends RuntimeException {

    private final List<String> problems;

    public InvalidConfigException(List<String> problems) {
        super(message(problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }

    private static String message(List<String> problems) {
        return problems.stream()
                .map(problem -> System.lineSeparator() + "  - " + problem)
                .collect(Collectors.joining("", "Invalid plugkit-db configuration:", ""));
    }
}
