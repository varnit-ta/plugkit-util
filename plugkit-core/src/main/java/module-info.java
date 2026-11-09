import org.jspecify.annotations.NullMarked;

/**
 * Shared building blocks for plugkit-util libraries.
 *
 * <p>The exported packages hold types applications see through the libraries: {@link
 * io.github.varnitta.plugkit.core.Health} and the {@link
 * io.github.varnitta.plugkit.core.exception.PlugkitException} hierarchy. The {@code internal} packages
 * are exported only to plugkit-util modules, so applications can't depend on them.
 */
@NullMarked
module io.github.varnitta.plugkit.core {
    requires static transitive org.jspecify;

    exports io.github.varnitta.plugkit.core;
    exports io.github.varnitta.plugkit.core.exception;

    exports io.github.varnitta.plugkit.core.internal.lifecycle to io.github.varnitta.plugkit.db;
    exports io.github.varnitta.plugkit.core.internal.redaction to io.github.varnitta.plugkit.db;
    exports io.github.varnitta.plugkit.core.internal.retry to io.github.varnitta.plugkit.db;
    exports io.github.varnitta.plugkit.core.internal.validation to io.github.varnitta.plugkit.db;
}
