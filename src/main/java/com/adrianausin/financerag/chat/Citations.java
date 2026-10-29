package com.adrianausin.financerag.chat;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the source numbers an answer cites: [1], [2][3], [1, 4]. */
final class Citations {

    private static final Pattern CITATION = Pattern.compile("\\[(\\d+(?:\\s*,\\s*\\d+)*)]");
    private static final Pattern CITATION_WITH_SPACE = Pattern.compile(" ?" + CITATION.pattern());

    private Citations() {
    }

    /** Cited numbers between 1 and {@code sourceCount}; anything else is a made-up reference and ignored. */
    static Set<Integer> cited(String answer, int sourceCount) {
        Set<Integer> numbers = new TreeSet<>();
        Matcher matcher = CITATION.matcher(answer);
        while (matcher.find()) {
            Arrays.stream(matcher.group(1).split(","))
                    .map(String::strip)
                    .map(Integer::parseInt)
                    .filter(n -> n >= 1 && n <= sourceCount)
                    .forEach(numbers::add);
        }
        return numbers;
    }

    /** The answer without its citation markers. */
    static String strip(String answer) {
        return CITATION_WITH_SPACE.matcher(answer).replaceAll("");
    }
}
