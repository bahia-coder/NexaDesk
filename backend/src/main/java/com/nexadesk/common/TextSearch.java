package com.nexadesk.common;

/**
 * Shared PostgreSQL full-text search fragments (French configuration, see migration V10).
 *
 * <p>Two query styles: a search box uses {@code websearch_to_tsquery} (every word must match, quotes and
 * {@code -word} are supported), while "find related content" uses {@link #ANY_TERM}: the text is stemmed by
 * {@code plainto_tsquery}, then its AND operators are turned into OR, so a long description still matches
 * documents that share only some of its words; {@code ts_rank} then favours those sharing the most.
 */
public final class TextSearch {

    /** tsquery matching any significant word of the {@code :text} parameter. */
    public static final String ANY_TERM = "replace(plainto_tsquery('french', :text)::text, ' & ', ' | ')::tsquery";

    /** tsquery for a search box: all words of {@code :text}. */
    public static final String ALL_TERMS = "websearch_to_tsquery('french', :text)";

    private TextSearch() {
    }
}
