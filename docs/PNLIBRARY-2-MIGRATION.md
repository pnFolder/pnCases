# Переход pnCases на pnLibrary 0.0.1-dev.5

Статус: выполнен 2026-10-22. Сборка и 42 модульных теста проходят. Живой запуск
на Paper 1.21.4 дошёл до включения pnLibrary и pnCases и подключения SQLite;
полная проверка на реальном сервере остаётся за владельцем.

## Что изменилось в upstream

`pnFolder/pnLibrary` переписан: теги 2.x удалены с GitHub, остался один релиз
`v0.0.1-dev.5` (19.10.2026). Пакет API сменился с `ru.privatenull.pnlibrary.api`
на `org.pnfolder.pnlibrary.api`. Старый тег `v2.1.0-beta.1` отдаёт 404, поэтому
прежний bootstrap не мог скачать библиотеку.

## Как устроена интеграция

- Компиляция: `compileOnly io.github.pnfolder:pnlibrary-api:0.0.1-dev.5` (Maven Central).
  API не попадает в JAR pnCases.
- Runtime: `pnlibrary-dependency.properties` хранит версию, URL, SHA-256 и главный
  класс плагина (`org.pnfolder.pnlibrary.bukkit.PnLibraryBukkitPlugin`).
  `PnLibraryBootstrap` скачивает `pnLibrary-0.0.1-dev.5-bukkit-java8.jar` (7 МБ,
  лимит загрузки 128 МиБ), проверяет SHA-256 и включает его в том же запуске.
  Если установлена другая pnLibrary (старая 2.x), pnCases останавливается с понятной
  причиной.
- Регистрация: `PnLibraryRuntime` вызывает `plugins.register(owner)` и
  `registerModule("pncases", ...)`: bStats (проект 32592), диаграмма
  `database_type`, обновления `PnFolder/pnCases` (канал STABLE, автозагрузка выключена).
  При disable закрывается регистрация, общий runtime не закрывается.
- `plugin.yml`: pnLibrary остаётся `softdepend` (hard `depend` не даёт bootstrap
  скачать библиотеку до проверки зависимостей).

## Что перенесено в pnCases

В новой pnLibrary нет этих возможностей, поэтому исходники pnLibrary 1.0.0
(коммит `25b55dd` в истории `pnFolder/pnLibrary`) перенесены в
`ru.privatenull.pncases.support.*` вместе с тестами и языковыми таблицами
(`pnlibrary/lang/*.json`):

| Пакет | Содержимое |
|---|---|
| `support.database` | DatabaseRouter/Executor, Jdbc, Mongo, Redis |
| `support.gui` (+`protocollib`) | GuiOpenAnimationService, GuiUpdateService, профили анимаций |
| `support.economy` | EconomyService (Vault, PlayerPoints) |
| `support.item`, `support.entity` | ItemFactory, HeadUtil, ItemStackCodec, VisualEntity |
| `support.localization` | ItemLocalization, MinecraftLocale |
| `support.text` | ColorUtil (MiniMessage, RGB, RGBA) |
| `support.compat`, `support.config` | BukkitCompat, ServerVersion, ServerCapabilities, AtomicYamlStore |

Предметы и `ColorUtil` не заменены классами новой pnLibrary сознательно: новый
`ItemText` не понимает MiniMessage и RGBA, а это изменило бы цвета.

## Зависимости, которые раньше приходили через старую библиотеку

`mongodb-driver-sync 5.2.1`, `HikariCP 5.1.0`, `mysql-connector-j 9.1.0`,
`jedis 7.5.3` (в JAR), `authlib 1.5.25`, `VaultAPI 1.7.1`, `playerpoints 3.3.5`
(compileOnly). `includeBuild('../pnLibrary')` удалён: сборка больше не требует
соседней папки.

## Известные ограничения сборки

- Репозиторий `repo.fancyinnovations.com` (FancyHolograms) на момент перехода не
  резолвился по DNS. Если Gradle не может его достать, повторите сборку позже.
- Gradle-тесты падали с `ClassNotFoundException`, когда проект лежал в папке с
  кириллицей (`Рабочий стол`). Из ASCII-пути все 42 теста проходят.
- Версия `0.0.1-dev.5` помечена как dev: API может меняться, перед обновлением
  сверяйте `CHANGELOG.md` upstream.
