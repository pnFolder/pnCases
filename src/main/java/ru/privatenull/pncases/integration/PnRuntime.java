package ru.privatenull.pncases.integration;

import ru.privatenull.pncases.support.task.TaskBackend;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** pnCases-facing boundary that keeps optional pnLibrary API types out of the plugin entry point. */
public interface PnRuntime extends AutoCloseable {

    /** Registers pnCases with pnLibrary. Called first, so that {@link #taskBackend()} is ready for the whole start. */
    void register(Supplier<String> databaseType, RuntimeData data);

    /** Reports that pnCases has started: shows the console card and publishes placeholders. */
    void enabled(String databaseType, int loadedCaseCount);

    /** Scheduling through pnLibrary's task scope. */
    TaskBackend taskBackend();

    String updateStatus(String currentVersion);

    /**
     * Loads the Minecraft translation table for the server version asynchronously and hands it to {@code target}.
     * Nothing is delivered when the version is unknown to the library or the table cannot be loaded.
     */
    void loadTranslations(String serverVersion, String locale, Consumer<Map<String, String>> target);

    String supportUrl();

    @Override
    void close();
}
