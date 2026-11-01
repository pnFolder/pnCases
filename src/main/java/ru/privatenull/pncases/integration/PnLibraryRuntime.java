package ru.privatenull.pncases.integration;

import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.localization.TranslationRequest;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer;
import org.pnfolder.pnlibrary.api.logging.MessageBox;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.remote.PolicyErrorAction;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider;
import org.pnfolder.pnlibrary.api.updates.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.tasks.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.TaskSpec;
import ru.privatenull.pncases.support.task.PluginTasks;
import ru.privatenull.pncases.support.task.TaskBackend;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Managed pnLibrary integration loaded only after the dependency is enabled. */
public final class PnLibraryRuntime implements PnRuntime {

    public static final String MINIMUM_VERSION = "0.0.1-dev.5";
    private static final String MODULE_ID = "pncases";
    private static final int BSTATS_PROJECT_ID = 32592;
    private static final String POLICY_SOURCE =
            "https://raw.githubusercontent.com/pnFolder/pnlibrary-policy/main/.pnlibrary/policies/PnCasesPolicy.java";
    private static final String FASTSTATS_TOKEN = "465dece7c2ff89da821767e756c2e10b";
    private static final String PLACEHOLDER_NAMESPACE = "pncases";

    private final JavaPlugin owner;
    private final PnLibrary runtime;
    private PluginRegistration registration;
    private ModuleContext module;
    private RuntimeData data;

    private PnLibraryRuntime(JavaPlugin owner, PnLibrary runtime) {
        this.owner = owner;
        this.runtime = runtime;
    }

    public static PnRuntime connect(JavaPlugin owner) {
        PnLibrary runtime = PnLibraryProvider.get();
        if (!runtime.isAtLeastVersion(MINIMUM_VERSION)) {
            throw new IllegalStateException("pnCases требует pnLibrary " + MINIMUM_VERSION
                    + " или новее, установлена " + runtime.getVersion());
        }
        return new PnLibraryRuntime(owner, runtime);
    }

    @Override
    public void register(Supplier<String> databaseType, RuntimeData runtimeData) {
        if (registration != null) throw new IllegalStateException("pnCases уже зарегистрирован в pnLibrary");
        registration = runtime.getPlugins().register(owner);
        try {
            module = registration.registerModule(MODULE_ID, builder -> builder
                    // A policy that cannot be checked (offline server, JRE without a compiler) never stops pnCases;
                    // only an explicit denial from the policy does.
                    .remotePolicy(RemotePolicySettings.builder()
                            .source(POLICY_SOURCE)
                            .onError(PolicyErrorAction.KEEP_RUNNING)
                            .support(PnLibraryIdentity.SUPPORT_URL)
                            .build())
                    .metrics(metrics -> metrics
                            .bStats(BSTATS_PROJECT_ID)
                            .fastStats(FASTSTATS_TOKEN)
                            .charts(charts -> charts.simplePie("database_type", databaseType::get)))
                    .diagnostics(owner.getDataFolder().toPath(), DiagnosticContainer.builder("pncases")
                            .snapshot(runtimeData::diagnostics)
                            .configuration(DiagnosticConfiguration.builder("config.yml")
                                    .secretKeyRegex("(?i).*(password|passwd|secret|token|uri|url|user(name)?)$")
                                    .build())
                            .build())
                    .updates("PnFolder", "pnCases", updater -> updater
                            .channel(UpdateChannel.STABLE)
                            .automaticDownload(false)
                            .artifact("(?i)^pnCases-[0-9].*\\.jar$", 17)));
        } catch (RuntimeException failure) {
            closeRegistration();
            throw failure;
        }
        Log.bind(module);
        this.data = runtimeData;
    }

    @Override
    public void started(String databaseType, int loadedCaseCount, String hologramProvider) {
        if (module == null) return;
        boolean published = registerPlaceholders(data);
        MessageBox box = module.getLifecycle().enabled()
                .ok("База данных", databaseType)
                .ok("Кейсы", String.valueOf(loadedCaseCount))
                .ok("Голограммы", hologramProvider);
        if (published) box.ok("Плейсхолдеры", "%pncases_*%");
        else box.warn("Плейсхолдеры", "не зарегистрированы");
        optional(box, "Vault", "экономика");
        optional(box, "PlayerPoints", "валюта очков");
        optional(box, "LuckPerms", "выдача прав");
        optional(box, "WorldGuard", "регионы");
        box.ok("Диагностика", "/pn debug " + MODULE_ID).show();
    }

    @Override
    public void stopped(String databaseType, int loadedCaseCount) {
        if (module == null || module.isClosed()) return;
        try {
            module.getLifecycle().disabled()
                    .ok("Кейсы", "меню и анимации закрыты (" + loadedCaseCount + ")")
                    .ok("База данных", "соединение закрывается (" + databaseType + ")")
                    .show();
        } catch (RuntimeException failure) {
            owner.getLogger().log(Level.FINE, "Не удалось вывести сообщение об остановке pnCases", failure);
        }
    }

    private void optional(MessageBox box, String plugin, String purpose) {
        if (owner.getServer().getPluginManager().isPluginEnabled(plugin)) box.ok(plugin, purpose);
        else box.skip(plugin, "не установлен, " + purpose + " недоступна");
    }

    /**
     * Publishes %pncases_keys_<case>%, %pncases_cases%, %pncases_opening% and %pncases_version%
     * through PlaceholderAPI. A failure here must never stop the plugin.
     */
    private boolean registerPlaceholders(RuntimeData data) {
        try {
            PlaceholderService service = module.getPlaceholders();
            service.register("keys", String.class, builder -> builder
                    .resolve(request -> data.keys(request.getPlayerId(), request.getParameters().get("value")))
                    .fallback("0")
                    .publishToPlaceholderApi(PLACEHOLDER_NAMESPACE, "keys"));
            service.register("cases", String.class, builder -> builder
                    .resolve(request -> String.valueOf(data.caseCount()))
                    .fallback("0")
                    .publishToPlaceholderApi(PLACEHOLDER_NAMESPACE, "cases"));
            service.register("opening", String.class, builder -> builder
                    .resolve(request -> String.valueOf(data.opening(request.getPlayerId())))
                    .fallback("false")
                    .publishToPlaceholderApi(PLACEHOLDER_NAMESPACE, "opening"));
            service.register("version", String.class, builder -> builder
                    .resolve(request -> data.version())
                    .publishToPlaceholderApi(PLACEHOLDER_NAMESPACE, "version"));
            return true;
        } catch (RuntimeException | LinkageError failure) {
            Log.warning("Плейсхолдеры pnCases не зарегистрированы; остальные функции работают.", failure);
            return false;
        }
    }

    @Override
    public String updateStatus(String currentVersion) {
        UpdateRegistration updates = module == null ? null : module.getUpdates();
        if (updates == null) return "&7проверяется";
        UpdateSnapshot snapshot = updates.getSnapshot();
        return switch (snapshot.getState()) {
            case CHECKING, DOWNLOADING -> "&7проверяется";
            case UPDATE_AVAILABLE, UPDATE_STAGED -> {
                String latest = snapshot.getLatestVersion();
                yield "&eдоступно &f" + currentVersion + " &8→ &#D8DF9D"
                        + (latest == null || latest.isBlank() ? "неизвестно" : latest);
            }
            case FAILED -> "&cпроверка недоступна";
            case INCOMPATIBLE, BLOCKED -> "&eновая версия несовместима с сервером";
            case FROZEN -> "&7обновления приостановлены";
            case ROLLED_BACK -> "&7выполнен откат версии";
            case UP_TO_DATE -> "&aактуальная версия";
        };
    }

    @Override
    public TaskBackend taskBackend() {
        return (action, delayTicks, periodTicks, async) -> {
            TaskSpec.Builder spec = TaskSpec.builder()
                    .delayTicks(delayTicks)
                    .execution(async ? TaskExecution.async() : TaskExecution.global())
                    .action(context -> action.run());
            if (periodTicks > 0L) spec.intervalTicks(periodTicks);
            org.pnfolder.pnlibrary.api.tasks.TaskHandle handle = module.getTasks().schedule(spec.build());
            return PluginTasks.handle(owner, !async, handle::cancel, handle::isCancelled);
        };
    }

    @Override
    public void loadTranslations(String serverVersion, String locale, Consumer<Map<String, String>> target) {
        if (module == null) return;
        try {
            MinecraftVersion version = MinecraftVersion.parse(serverVersion);
            if (!version.getKnown()) {
                Log.info("pnLibrary не знает версию сервера " + serverVersion
                        + ", названия предметов берутся из встроенного файла.");
                return;
            }
            TranslationRequest request = TranslationRequest.builder().version(version).locale(locale).build();
            module.getLocalization().load(request).whenComplete((bundle, failure) -> {
                if (failure != null || bundle == null) {
                    Log.info("Переводы Minecraft недоступны (нет сети?), "
                            + "названия предметов берутся из встроенного файла.");
                    return;
                }
                target.accept(bundle.locale(locale).translations());
            });
        } catch (RuntimeException | LinkageError failure) {
            owner.getLogger().log(Level.FINE, "Не удалось запросить переводы у pnLibrary", failure);
        }
    }

    @Override
    public String supportUrl() {
        return PnLibraryIdentity.SUPPORT_URL;
    }

    @Override
    public void close() {
        Log.bind((ModuleContext) null);
        module = null;
        closeRegistration();
    }

    private void closeRegistration() {
        PluginRegistration closing = registration;
        registration = null;
        if (closing != null && !closing.isClosed()) closing.close();
    }
}
