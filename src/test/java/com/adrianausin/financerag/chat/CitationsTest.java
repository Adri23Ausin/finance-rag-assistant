package com.adrianausin.financerag.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CitationsTest {

    @Test
    void readsSingleAdjacentAndListedCitations() {
        assertThat(Citations.cited("Income rose [1]. Costs fell [2][3], see [1, 4].", 5)).containsExactly(1, 2, 3, 4);
    }

    @Test
    void ignoresNumbersOutsideTheSources() {
        assertThat(Citations.cited("Per [0] and [6], and [2].", 5)).containsExactly(2);
    }

    @Test
    void ignoresBracketsThatAreNotCitations() {
        assertThat(Citations.cited("Net income [EUR m] was 1,204 [restated].", 5)).isEmpty();
    }
}
