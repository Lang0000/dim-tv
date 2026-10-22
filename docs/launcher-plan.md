# Лаунчер: аналіз і план заміни (2026-10-21)

## Поточний стан

Головний екран: Google TV launcher (`com.google.android.apps.tv.launcherx` 1.0.994451607), встановлений як HOME за замовчуванням.

Пам'ять, яку займає все, що стосується лаунчера (через 4 хв після завантаження):

| Процес | PSS |
|---|---|
| com.google.android.apps.tv.launcherx | 88 МБ |
| launcherx:coreservices | 21 МБ |
| com.mitv.tvhome.atv (вміст Xiaomi для головного екрана) | 24 МБ |
| com.google.android.tungsten.setupwraith (майстер налаштування, висить у фоні) | 19 МБ |
| **Разом** | **~152 МБ** з 1,8 ГБ |

Хто ще може бути HOME (`query-activities HOME`):

| Пакет | Пріоритет | Що це |
|---|---|---|
| launcherx | 2 | поточний Google TV |
| tungsten.setupwraith / RecoveryActivity | 1 | екран відновлення майстра налаштування |
| com.duokan.factorytest / FactoryLauncher | 0 | **заводське сервісне меню**: не повинно стати головним екраном |
| tv.settings / FallbackHome | -1000 | заглушка |

Висновок: якщо просто вимкнути launcherx, кнопка Home може відкрити RecoveryActivity або заводське меню. Тому новий лаунчер спершу треба явно зробити HOME (`cmd package set-home-activity`), і лише потім вимикати старий.

## Кандидати

| | Projectivy Launcher | FLauncher |
|---|---|---|
| Де взяти | Google Play (`com.spocky.projengmenu`) | Google Play / GitHub, open source |
| Вага | легкий | найлегший |
| HDMI-входи на головному екрані | так | ні, лише через окремий додаток |
| Підтримка | активна | розвиток практично зупинився |
| Реклама | немає (є платні додаткові функції) | немає |
| Перехоплення кнопки Home без вимкнення стоку | так (через службу спеціальних можливостей) | ні |

**Рекомендація: Projectivy Launcher.** Він показує HDMI-входи, і його можна випробувати, не вимикаючи стоковий лаунчер.

## Що втрачається з вимкненням launcherx

- Рекомендації Google TV («Для вас»), список перегляду, «Продовжити перегляд» з різних додатків, профілі.
- Панель швидких налаштувань Google TV у правому верхньому куті. Налаштування лишаються доступні як окремий додаток.
- Голосовий пошук (katniss), Chromecast, YouTube, Netflix та інші додатки працюють як і раніше.

## План

1. Встановити Projectivy з Play Маркету (відкрити його сторінку на ТВ через adb, натиснути «Встановити» пультом).
2. Зробити його HOME: `adb shell cmd package set-home-activity <компонент Projectivy>`; launcherx поки лишити ввімкненим. Перевірити Home, HDMI-входи, пульт, налаштування.
3. Пожити з цим 1–2 дні.
4. Якщо все добре, вимкнути `com.google.android.apps.tv.launcherx`, перезавантажити, перевірити, заміряти пам'ять. Очікувана економія ~100 МБ (сам launcherx і coreservices).

## Хід виконання

- 2026-10-21 04:07: встановлено Projectivy Launcher 4.72 з Play Маркету.
- `cmd package set-home-activity com.spocky.projengmenu/.ui.home.MainActivity` → Success, у preferred-xml записано. Але поки launcherx увімкнений (системний, priority 2), Home все одно відкриває Google TV. Цей запис спрацює, коли launcherx буде вимкнено, і не дасть кнопці Home відкрити заводське меню.
- Тому на етапі 2 Home перехоплює сам Projectivy через свою службу спеціальних можливостей (`com.spocky.projengmenu/.services.ProjectivyAccessibilityService`). Її вмикає користувач на ТВ під час онбордингу Projectivy або в його налаштуваннях («Override launcher»).
- Онбординг Projectivy пройдено, але `enabled_accessibility_services=null`: служба не ввімкнена, Home досі відкриває Google TV.
- Загальний інтент `android.settings.ACCESSIBILITY_SETTINGS` на цьому ТВ веде в заглушку (frameworkpackagestubs). Справжній екран: `am start -n com.android.tv.settings/.oemlink.AccessibilitySettingsActivity`. Відкрито на ТВ, там пункт Projectivy Launcher → «Увімкнути».
- Користувач увімкнув службу: `enabled_accessibility_services=com.spocky.projengmenu/...ProjectivyAccessibilityService`, `accessibility_enabled=1`.
- Перевірка Home через adb (`input keyevent KEYCODE_HOME`) з налаштувань і з Play Маркету: обидва рази відкрився `com.spocky.projengmenu/.ui.home.MainActivity`. **Етап 2 працює.**
- MemAvailable ~741 МБ: зараз у пам'яті обидва лаунчери, тому менше, ніж раніше. Економія буде лише на етапі 4, після вимкнення launcherx.
- Фізична кнопка Home на пульті, HDMI-входи в Projectivy, пульт загалом: _перевіряє користувач_

Відкат:

```
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
```
