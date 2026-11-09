package io.github.varnitta.plugkit.db.internal.statement;

import java.util.Set;

/**
 * How a database marks text that isn't SQL code, so {@link NamedSql} doesn't mistake a {@code :name}
 * inside it for a parameter.
 *
 * <p>Each dialect supplies its own rules: PostgreSQL has dollar-quoted text, MySQL quotes identifiers
 * with backticks, allows backslash escapes in strings and treats {@code #} as a comment.
 *
 * @param quoteCharacters        characters that open and close quoted text or identifiers
 * @param backslashEscapesQuotes whether {@code \'} inside quoted text is an escaped quote
 * @param hashStartsComment      whether {@code #} starts a comment that runs to the end of the line
 * @param dashCommentNeedsSpace  whether {@code --} starts a comment only when followed by whitespace
 * @param dollarQuoting          whether {@code $$ … $$} and {@code $tag$ … $tag$} quote text
 */
public record SqlSyntax(
        Set<Character> quoteCharacters,
        boolean backslashEscapesQuotes,
        boolean hashStartsComment,
        boolean dashCommentNeedsSpace,
        boolean dollarQuoting) {

    public SqlSyntax {
        quoteCharacters = Set.copyOf(quoteCharacters);
    }
}
