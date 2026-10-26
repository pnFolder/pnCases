<h1 align="center">pnCases</h1>

<p align="center">
  Кейсы для Minecraft-сервера: красивые анимации открытия, виртуальные ключи<br>
  и удобная настройка прямо в игре, без правки файлов.
</p>

<p align="center">
  <a href="https://github.com/PnFolder/pnCases/releases/latest">
    <img src="https://img.shields.io/badge/Скачать-последняя%20версия-EFF7B9?style=for-the-badge&labelColor=17241F" alt="Скачать">
  </a>
  <a href="https://discord.gg/SZxPP9surw">
    <img src="https://img.shields.io/badge/Discord-Поддержка-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Paper%20%2F%20Purpur-1.16.5%20и%20новее-EFF7B9?style=flat-square" alt="Paper и Purpur">
  <img src="https://img.shields.io/badge/Java-17%2B-FCA865?style=flat-square" alt="Java 17+">
  <img src="https://img.shields.io/badge/База-SQLite%20%7C%20MySQL%20%7C%20MongoDB%20%7C%20Redis-65D1FC?style=flat-square" alt="Базы данных">
</p>

## Что это

pnCases добавляет на сервер кейсы. Игрок получает ключ, нажимает на блок кейса, смотрит анимацию и получает награду.
Кейс создаётся и настраивается в игре через окно **Machine**: награды добавляются кликом по предмету, шансы меняются
кнопками, а плагин сам подскажет, если что-то мешает кейсу работать.

## Возможности

**Для игроков**
- 14 анимаций открытия: Наковальня, Динамит, Портал, Круг фортуны, Дыхание дракона, Голодный мимик, Призма и другие.
  Можно выбрать любимую или собрать набор избранных, тогда анимация будет выпадать случайно.
- Открытие сразу нескольких кейсов (Shift + ПКМ): одна анимация и общее окно со всеми наградами.
- Предпросмотр содержимого кейса и история последних выпавших наград.
- Передача своих ключей другому игроку: `/pncases gift <игрок>`.

**Для администраторов**
- **Machine** в игре: создание кейса, награды из инвентаря, шансы кликами, привязка к блоку, ключ для проверки,
  откат правки, симуляция 100 000 открытий.
- Награды: предметы и головы, деньги (Vault), поинты (PlayerPoints), группы и права (LuckPerms),
  любые консольные команды.
- Редкости, гарант редкости, свои сообщения и объявление на весь сервер для каждого кейса.
- Голограммы над кейсом (FancyHolograms или DecentHolograms) и частицы-витрины.
- Один виртуальный ключ на кейс. Данные хранятся в SQLite, MySQL, MongoDB или Redis.
- Плейсхолдеры PlaceholderAPI и события для других плагинов.
- Копия настроек при каждом обновлении, проверка кейса и `/pndebug` для поддержки.

## Установка

1. Скачайте JAR со страницы [релизов](https://github.com/PnFolder/pnCases/releases/latest) и положите в `plugins/`.
2. Установите [ProtocolLib](https://github.com/dmulloy2/ProtocolLib) и один из плагинов голограмм:
   FancyHolograms или DecentHolograms.
3. Запустите сервер. Если нужной библиотеки pnLibrary нет, pnCases скачает её сам (около 66 МБ).
4. Выполните `/pncases machine`, нажмите «Новый кейс» и введите название.
5. В разделе «Награды» кликайте по предметам в своём инвентаре: они станут наградами.
6. Посмотрите на нужный блок и нажмите «Блок кейса».
7. «Ключ для проверки» выдаст вам ключ, чтобы сразу попробовать открыть кейс.

Обновляйте плагин полным перезапуском сервера, а не через PlugManX.

## Что нужно для работы

| Плагин | Нужен ли | Зачем |
|---|---|---|
| Paper или Purpur 1.16.5+ | да | Сервер |
| ProtocolLib | да | Анимации и обновление меню |
| FancyHolograms или DecentHolograms | да, один из двух | Голограммы |
| Vault | нет | Денежные награды |
| PlayerPoints | нет | Награды-поинты |
| LuckPerms | нет | Группы и права |
| PlaceholderAPI | нет | Плейсхолдеры `%pncases_*%` |
| WorldGuard | нет | Мобы в анимациях появляются даже в защищённых регионах |

## Команды

| Команда | Что делает |
|---|---|
| `/pncases machine [кейс]` | Открыть Machine: список кейсов или настройку выбранного |
| `/pncases createcase <id>` | Создать кейс |
| `/pncases copycase <откуда> <куда>` | Скопировать настройки кейса |
| `/pncases setcase <кейс>` | Привязать кейс к блоку, на который вы смотрите |
| `/pncases delcase <кейс>` | Удалить кейс и его ключ |
| `/pncases givekey <игрок> <кейс> <число> [-s]` | Выдать ключи (`-s` без уведомления) |
| `/pncases takekey <игрок> <кейс> <число>` | Забрать ключи |
| `/pncases gift <игрок>` | Подарить свои ключи другому игроку |
| `/pncases reload` | Перезагрузить настройки |

**Права:** `pncases.admin` для администраторов, `pncases.gift` для передачи ключей (по умолчанию никому не выдан).

## Пример кейса

Каждый кейс лежит в отдельном файле `plugins/pnCases/cases/<id>.yml`. Всё это можно сделать и в Machine,
файл нужен, если удобнее править текстом:

```yaml
display-name: '&#429F91Донат-кейс'

hologram:
  enabled: true
  lines:
    - '{display-name}'
    - '&7ПКМ &8— &fоткрыть'

rewards:
  - type: ITEM
    chance: 70
    rarity: COMMON
    item:
      material: DIAMOND
      amount: 3
      name: '&b3 алмаза'
    message: '&aВы получили {reward}&a!'

  - type: PERSONAL
    chance: 5
    rarity: LEGENDARY
    commands:
      - 'give {player} golden_apple 1'
    name: '&eЗолотое яблоко'
    visual:
      material: GOLDEN_APPLE
```

| Тип награды | Что выдаёт |
|---|---|
| `ITEM` | Предмет или голову |
| `VAULT` | Деньги |
| `PLAYERPOINTS` | Поинты |
| `LUCKPERMS` | Группу или право, можно на срок |
| `PERSONAL` | Одну или несколько консольных команд |

## Плейсхолдеры и события

Для PlaceholderAPI:

| Плейсхолдер | Значение |
|---|---|
| `%pncases_keys_<кейс>%` | Ключи игрока |
| `%pncases_cases%` | Сколько кейсов загружено |
| `%pncases_opening%` | `true`, пока игрок открывает кейс |
| `%pncases_version%` | Версия плагина |

Для разработчиков есть события `CaseOpenEvent` (можно отменить), `CaseRewardGrantEvent` и `CaseKeyGiveEvent`
в пакете `ru.privatenull.pncases.api.event`.

## Настройка

```text
plugins/pnCases/
├── cases/<id>.yml     # каждый кейс отдельно
├── config.yml         # база данных, язык, функции
├── gui.yml            # тексты меню
├── messages.yml       # сообщения
└── backups/           # копии настроек при обновлении
```

Тип базы данных выбирается в `config.yml` (`database.type`: `SQLITE`, `MYSQL`, `MONGODB` или `REDIS`), смена требует
перезапуска. При обновлении плагина новые поля добавляются в ваши файлы сами, ваши значения не затираются.

## Поддержка

Если что-то не работает или есть идея, пишите в [Discord](https://discord.gg/SZxPP9surw) или заведите
[issue](https://github.com/PnFolder/pnCases/issues). Список изменений по версиям: [релизы](https://github.com/PnFolder/pnCases/releases).

Лицензия: [MIT](LICENSE).
