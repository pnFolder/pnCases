<p align="center">
  <img src="assets/pncases-cover-v2.png" alt="pnCases — кейсы для Minecraft" width="100%">
</p>

<h1 align="center">pnCases</h1>

<p align="center">
  Настраиваемая система кейсов для Paper и Purpur с анимациями,
  виртуальными ключами, витринами, голограммами и Machine GUI.
</p>

<p align="center">
  <a href="https://github.com/Dy6HiLa/pnCases/releases/download/v1.5.3/pnCases-1.5.3.jar">
    <img src="https://img.shields.io/badge/Скачать-1.5.3-EFF7B9?style=for-the-badge&labelColor=17241F" alt="Скачать pnCases 1.5.3">
  </a>
  <a href="https://github.com/Dy6HiLa/pnCases/releases/tag/v1.5.3">
    <img src="https://img.shields.io/badge/Что%20нового-1.5.3-65D1FC?style=for-the-badge&labelColor=17241F" alt="Изменения pnCases 1.5.3">
  </a>
  <a href="https://discord.gg/SZxPP9surw">
    <img src="https://img.shields.io/badge/Discord-Поддержка-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord pnCases">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Paper%20%2F%20Purpur-1.16.5--1.21.11-EFF7B9?style=flat-square" alt="Paper и Purpur 1.16.5–1.21.11">
  <img src="https://img.shields.io/badge/Java-17%2B-FCA865?style=flat-square" alt="Java 17+">
  <img src="https://img.shields.io/badge/Storage-SQLite%20%7C%20MySQL%20%7C%20MongoDB%20%7C%20Redis-65D1FC?style=flat-square" alt="SQLite, MySQL, MongoDB и Redis">
</p>

## Возможности

- Каждый кейс хранится отдельно в `plugins/pnCases/cases/<id>.yml`.
- Создание и полная настройка кейсов без заранее заданных типов.
- Один виртуальный ключ на кейс: ID ключа всегда совпадает с ID кейса.
- Типы наград: `ITEM`, `VAULT`, `PLAYERPOINTS`, `LUCKPERMS`, `PERSONAL`.
- Кастомные предметы и головы через Base64.
- Редкости `COMMON`, `UNCOMMON`, `RARE`, `EPIC`, `LEGENDARY`, `MYTHIC`.
- Настраиваемые шансы, сообщения наград и broadcast для каждого кейса; в GUI всегда показывается фактический нормализованный процент выпадения.
- Предпросмотр содержимого, история открытий и выбор анимации игроком.
- Machine GUI для настройки меню, голограммы, витрины, анимации и покупки ключа за опыт.
- Единый стиль карточек настроек с кратким описанием, текущим значением и подсказками кнопок.
- Пакетная анимация открытия и точечное обновление GUI через обязательный ProtocolLib-транспорт pnLibrary.
- Голограммы через FancyHolograms или DecentHolograms.
- Локализованные названия предметов, блоков, чар и зелий через встроенные таблицы pnLibrary.
- Денежные и PlayerPoints-награды через общую экономическую абстракцию pnLibrary.
- Хранение ключей, истории, выбранных анимаций и pending-наград через общий DatabaseRouter: SQLite, MySQL, MongoDB или Redis.

## Установка

1. Скачайте [pnCases-1.5.3.jar](https://github.com/Dy6HiLa/pnCases/releases/download/v1.5.3/pnCases-1.5.3.jar).
2. Поместите JAR в папку `plugins/`.
3. Установите FancyHolograms или DecentHolograms.
4. Полностью перезапустите сервер.
5. Создайте кейс командой `/pncases createcase <id>`.
6. Настройте `plugins/pnCases/cases/<id>.yml` или используйте `/pncases machine <id>`.
7. Посмотрите на нужный блок и выполните `/pncases setcase <id>`.

Для установки нового JAR используйте полный перезапуск сервера. Повторное включение уже загруженной pnCases 1.5.3 через PlugManX защищено от закрытого classloader, но полноценный restart остаётся рекомендуемым способом обновления плагина и его зависимостей.

## Зависимости

| Зависимость | Статус | Назначение |
|---|---:|---|
| Paper / Purpur | обязательно | Сервер 1.16.5–1.21.11 |
| LuckPerms | опционально | Группы и права |
| Vault | опционально | Денежные награды |
| PlayerPoints | опционально | Поинты |
| FancyHolograms или DecentHolograms | обязательно выбрать один | Голограммы кейсов и анимаций |
| ProtocolLib | обязательно | Пакетная анимация, заголовки и обновление слотов GUI |

## Быстрый старт

```text
/pncases createcase donate
/pncases machine donate
/pncases setcase donate
/pncases givekey Player donate 1
```

После `/pncases createcase donate` будет создан файл:

```text
plugins/pnCases/cases/donate.yml
```

Кейс автоматически использует один виртуальный ключ с ID `donate`. Отдельно указывать тип ключа, его ID или количество для открытия не требуется.

## Команды

| Команда | Описание |
|---|---|
| `/pncases` | Панель администратора |
| `/pncases reload` | Перезагрузить настройки |
| `/pncases createcase <id>` | Создать новый кейс |
| `/pncases machine <кейс>` | Открыть Machine GUI |
| `/pncases setcase <кейс>` | Привязать кейс к выбранному блоку |
| `/pncases delcase <кейс>` | Удалить кейс и его виртуальный ключ |
| `/pncases givekey <игрок> <кейс> <количество> [-s]` | Выдать ключи; `-s` скрывает уведомление игроку |
| `/pncases takekey <игрок> <кейс> <количество>` | Забрать ключи |

Право администратора:

```text
pncases.admin
```

## Конфигурация кейса

Сокращённый пример:

```yaml
blocks: []

display-name: '&#429F91Донат-кейс'

hologram:
  enabled: true
  type: TEXT
  y: 1.5
  lines:
    - '{display-name}'
    - '&7ПКМ &8— &fоткрыть'

idle-particles:
  enabled: true
  theme: DRAGON
  radius: 0.85
  height: 1.35
  speed: 0.14
  interval_ticks: 2
  view_distance: 28

gui:
  title: '{display-name}'
  size: 54
  open_slot: 22
  animation_slot: 49
  preview_enabled: true
  history:
    slots: [45, 46, 47, 48, 50, 51, 52, 53]
  button:
    material: CHEST
    name: '{display-name}'
    lore:
      - ''
      - ' &#A6B0BAОткрытие и просмотр содержимого кейса'
      - ''
      - ' &#C2CBD3Ключи'
      - ' &#69747E↳ &#A7F3D0{have} / {need}'
      - ''
      - ' &#FFB347[&#FFA43C Л&#FF9536К&#FF8630М &#FF772A] &f{left_action}'
      - ' &#FF8A8A[&#FF7979 П&#F96868К&#F25757М &#EB4646] &f{right_action}'
      - ''

cost:
  xp-purchase:
    enabled: false
    levels: 0

broadcast:
  enabled: true
  lines:
    - '&#EFF7B9▸ &fИгрок {player} открыл {case}'
    - '&#EFF7B9▸ &fНаграда: {reward}'

rewards:
  - type: ITEM
    chance: 100
    rarity: COMMON
    item:
      material: DIAMOND
      amount: 3
      name: '&b3 алмаза'
    message: '&aВы получили {reward}&a!'
```

Полный пример со всеми типами наград и Base64-головами находится в [`cases/test.yml`](cases/test.yml).

## Награды

| Тип | Что выдаёт | Зависимость |
|---|---|---|
| `ITEM` | Предмет или Base64-голову | нет |
| `VAULT` | Деньги | Vault и экономика |
| `PLAYERPOINTS` | Поинты | PlayerPoints |
| `LUCKPERMS` | Группу или permission | LuckPerms |
| `PERSONAL` | Консольную команду | нет |

Пример персональной команды:

```yaml
- type: PERSONAL
  chance: 10
  rarity: LEGENDARY
  command: 'give {player} golden_apple 1'
  name: '&eЗолотое яблоко'
  visual:
    material: GOLDEN_APPLE
    name: '&eЗолотое яблоко'
```

В `PERSONAL` доступны `{player}` и `{uuid}`. Для успешного сообщения конкретной награды используется поле `message`.

## Анимации открытия

Доступные анимации:

- Наковальня;
- Динамит;
- Портал;
- Отравление;
- Круг фортуны;
- Осада разбойников;
- Водяная капсула;
- Охота на стражей;
- Дыхание дракона;
- Голодный мимик;
- Грозовая дуэль;
- Искажение времени.

Выбор игрока сохраняется отдельно для каждого кейса. На Minecraft 1.20.x и 1.21.x работает один полный набор современных анимаций с одинаковым сценарием и дизайном. На 1.17–1.19 используется совместимый режим, а на 1.16.5 — «Круг фортуны».

Выбор анимации открывается одним списком без разделения на обычные и мобовые сценарии.

Все обычные сцены используют единый фазовый таймлайн. Интерактивные сценарии
«Отравление» и «Охота на стражей» используют отдельный общий контроллер,
поскольку их продолжительность зависит от действий игрока. Поэтому новая
анимация описывает только фазы и собственную сцену, а не копирует планировщик,
проверки онлайна, очистку сущностей и безопасное завершение.

В «Круге фортуны» показываются визуальные предметы всех наград кейса. Колесо
плавно замедляется с начала вращения, останавливается на заранее выбранной
награде и переводит этот же предмет в финальную сцену. Размер элементов кольца
автоматически уменьшается при большом количестве наград.

В «Искажении времени» над кейсом собирается циферблат, ход часов постепенно
ускоряется, а Часовщик прокручивает разрушение кейса назад. Ошибка времени
создаёт его будущего двойника: две версии персонажа перетягивают состояние
кейса, меняются местами временными скачками, после чего вся сцена проигрывается
в обратном порядке. Часовщик стирает себя из времени, и после паузы награда
выходит одна — без перекрывающих её частиц.

Текст названия выпавшей награды создаётся только через FancyHolograms или DecentHolograms. Встроенного `TextDisplay` fallback нет: при отсутствии совместимого провайдера pnCases останавливает загрузку с понятной ошибкой.

Стиль текста награды задаётся глобально в `config.yml`:

```yaml
animations:
  reward-hologram:
    scale: 1.45
    height-offset: 0.16
    visibility-distance: 48
    brightness: 15
    movement-ticks: 2
    reveal-delay-ticks: 6
```


## Витрина

Витрина отображает только анимацию частиц без предмета или сундука над кейсом.

Доступные темы:

- Арканный ритуал;
- Разлом пустоты;
- Штормовая клетка;
- Огненный феникс;
- Ядовитая кобра;
- Ледяная корона;
- Плач душ;
- Зов левиафана;
- Небесные крылья;
- Драконья орбита.

Тема и состояние витрины меняются через `/pncases machine <кейс>` или в секции `idle-particles`.

## Файлы

```text
plugins/pnCases/
├── cases/
│   └── <id>.yml
├── config.yml
├── gui.yml
├── messages.yml
└── data.db
```

- `config.yml` — функции, база данных, локализация, редкости и провайдер голограмм.
- `gui.yml` — тексты интерфейсов и единый шаблон предмета предпросмотра.
- `messages.yml` — команды, ошибки и системные сообщения.
- `cases/<id>.yml` — полная конфигурация конкретного кейса.

Необязательные функции задаются напрямую, без системы модулей:

```yaml
features:
  rarity: true
  preview: true

rarity:
  default: "&f◆ {id}"
  common: "&7◇ Обычная"
  uncommon: "&a◆ Необычная"
  rare: "&9◆ Редкая"
  epic: "&5✦ Эпическая"
  legendary: "&6★ Легендарная"
  mythic: "&d✹ Мифическая"
```

GUI-анимация и анимация заголовка являются частью интерфейса и всегда включены.

### Автоматическое обновление YAML

При запуске и перед `/pncases reload` плагин автоматически синхронизирует
`config.yml`, `gui.yml`, `messages.yml` и каждый `cases/*.yml` со схемой новой версии.
Добавленные поля появляются автоматически, удалённые исчезают, неизменённые
стандартные значения обновляются, а пользовательские значения сохраняются.
Списки наград, привязанные блоки и пользовательская раскладка кейса при этом
не заменяются стандартными значениями.
Однозначно перемещённые поля переносятся по новому пути без ручного списка
миграций в коде.

Предыдущие встроенные шаблоны хранятся во внутреннем файле
`.pncases/yaml-templates.yml`. Это не резервная копия пользовательских конфигов:
файл нужен только для автоматического трёхстороннего сравнения следующего
обновления. Запись YAML проходит через временный файл с проверочным чтением и
атомарной заменой; при ошибке активные кейсы остаются без изменений.

### Хранилище

```yml
database:
  type: SQLITE # SQLITE, MYSQL, MONGODB или REDIS
  sqlite:
    file: data.db
  mysql:
    host: localhost
    port: 3306
    database: pncases
    username: root
    password: ""
  mongodb:
    uri: mongodb://localhost:27017
    database: pncases
    collection: pncases
  redis:
    uri: redis://localhost:6379/0
    namespace: pncases
```

Все репозитории pnCases получают выбранное подключение из одного `DatabaseRouter`
pnLibrary. Смена `database.type` требует полного перезапуска сервера.

### Локализация предметов

```yml
localization:
  locale: ru_ru # ru_ru или en_us
```

Названия материалов, блоков, зачарований и зелий загружаются из встроенных таблиц
pnLibrary. Изменение локали применяется командой `/pncases reload`.

## Поддержка

- [Discord](https://discord.gg/SZxPP9surw)
- [GitHub Issues](https://github.com/Dy6HiLa/pnCases/issues)
- [Релизы](https://github.com/Dy6HiLa/pnCases/releases)
- [Лицензия MIT](LICENSE)
