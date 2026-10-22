# Вимкнені пакети на Xiaomi TV A 43 2025 (192.168.31.60)

Вимкнено через `adb shell pm disable-user --user 0 <пакет>`. Повернути пакет:

```
adb shell pm enable <пакет>
```

## Партія 1 — 2026-10-21 03:4x (аналітика і демо)

| Пакет | Що це | Шлях |
|---|---|---|
| com.miui.tv.analytics | Аналітика Xiaomi (MiTVBiAnalytics 1.33.77), ~18 МБ RAM | /system/app/MiTVBiAnalytics |
| com.xiaomi.statistic | Статистика Xiaomi (MiTVStat) | /system/app/MiTVStat |
| fusion.android.tv.demo | Демо-режим для вітрин магазину (RetailDisplay) | /system_ext/priv-app/RetailDisplay |

MemAvailable: 822 980 kB до вимкнення, 866 484 kB одразу після.

Перевірити після перезавантаження: пульт, HDMI-входи, YouTube, Netflix, ефірне ТБ.
Після `adb reboot` (2026-10-21 03:47, завантаження ~30 с): усі три пакети лишились disabled, процесів немає. Своп після завантаження: 80 МБ.
Пульт, HDMI і додатки після перезавантаження: працюють (перевірено користувачем).

## Партія 2 — 2026-10-21 (Mi TV+ і канали з рекламою)

| Пакет | Що це | Шлях |
|---|---|---|
| com.mitv.tvhome.mitvplus | Mi TV+: безкоштовні канали Xiaomi з рекламою (3.9.16) | /data/app (оновлений системний) |
| com.mitv.tvhome.michannel | Mi Channel: рекомендаційні канали Xiaomi на головному екрані (2.7) | /data/app (оновлений системний) |

На момент вимкнення обидва не були запущені, тож пам'ять одразу не змінилася (MemAvailable ~841 МБ).
Після `adb reboot` (2026-10-21 03:52, завантаження ~27 с): усі 5 пакетів обох партій лишились disabled, процесів немає. MemAvailable ~843 МБ через хвилину після старту.
Пульт, HDMI і додатки після перезавантаження: працюють (перевірено користувачем).

## Партія 3 — 2026-10-21 (Play Ігри, TalkBack, Mi Music, Mi Gallery)

| Пакет | Що це | Шлях |
|---|---|---|
| com.google.android.play.games | Google Play Ігри (2025.09) | /data/app (оновлений системний) |
| com.google.android.marvin.talkback | TalkBack, озвучення екрана (14.2.0); служба спеціальних можливостей не була активна | /data/app (оновлений системний) |
| com.xiaomi.mimusic2 | Mi Music (2.0.58) | /system/priv-app/TVMusic |
| com.mitv.gallery | Mi Gallery, перегляд фото з USB (1.3.12) | /system/priv-app/MiTVGallery |

На момент вимкнення жоден не був запущений. Amazon Prime Video і YouTube Music не чіпали.
Після `adb reboot` (2026-10-21 03:57, завантаження ~24 с): усі 9 пакетів трьох партій лишились disabled, жоден не запущений. MemAvailable ~876 МБ через хвилину після старту.
Пульт, HDMI і додатки після перезавантаження: працюють (перевірено користувачем).

## Далі

- Через кілька годин звичайного перегляду зняти `adb shell dumpsys meminfo` і порівняти з `diag/meminfo.txt` (локально) (до дебладінгу: доступно ~823 МБ, своп 311 МБ).
- Записувати дату й час кожного самовільного перезавантаження, потім зняти `adb shell getprop persist.sys.boot.reason.history`.

## Google TV launcher — 2026-10-21

| Пакет | Що це |
|---|---|
| com.google.android.apps.tv.launcherx | Головний екран Google TV. Замінено власним лаунчером «Дім» (`launcher/`) |

Повертати лише разом із вимкненням «Дому» або його служби, див. [`launcher/README.md`](../launcher/README.md): `adb shell pm enable com.google.android.apps.tv.launcherx`.
Після `adb reboot` лишається вимкненим; «Дім» відкривається після старту, Home і Back працюють.

## Не вдалося: com.mitv.tvhome.atv (PatchWall)

`pm disable-user` спрацював, але після `adb reboot` пакет знову `enabled=0` і стартує через BootCompletedReceiver (~22 МБ): прошивка Xiaomi повертає його під час завантаження.
`pm uninstall -k --user 0` не робили: користувач вирішив залишити PatchWall як є.

## Не вдалося: com.google.android.katniss (Google Асистент)

2026-10-21: `pm disable-user` спрацював, процес зупинився. Але після `adb reboot` пакет знову `enabled=0`, а `katniss:interactor` запускається сам (~77 МБ). Він лишається системним голосовим сервісом (`voice_interaction_service`). Як і PatchWall, його повертає прошивка під час завантаження; Google TV launcher, на відміну від них, лишається вимкненим.
Без Google TV launcher Асистент однаково не виконує запитів (див. [`launcher/README.md`](../launcher/README.md)).
`pm uninstall -k --user 0` не робили: користувач вирішив залишити Асистента як є.

## Партія 4 — 2026-10-23 00:1x (за списком cleanyourtv для MiTV-MOOR2)

Джерело: [examples/xiaomi-mitv-moor2/debloat.txt](https://github.com/Gathin23/cleanyourtv/blob/main/examples/xiaomi-mitv-moor2/debloat.txt). Ці пакети вже вимикали на ТВ тієї ж моделі (там прошивка 2674).

| Пакет | Що це |
|---|---|
| com.xiaomi.tvqs.overseas | Телеметрія Xiaomi (Firebase, будильник `tvqs.action.UPLOAD`) |
| com.mitv.tvhome.oemtab | Рекламна вкладка Xiaomi в Google TV (Google TV і так вимкнений) |
| com.xm.webcontent | Промо-WebView Xiaomi |
| com.google.android.partnersetup | Атрибуція партнера Google (RLZ) |
| com.xiaomi.mitv.tvmanager | «TV Manager», чистильник; найчастіше закривався через LOW_MEMORY |
| com.mitv.toolhouse | Службовий набір Xiaomi |
| com.xiaomo.tv.milegal | Юридичні тексти |
| android.autoinstalls.config.xiaomi.amelie | Тиха автоустановка партнерських додатків |
| com.xiaomi.mitv.smartshare | Mi Cast (Chromecast працює без нього) |
| com.xiaomi.wfdsinkhelperservice | Miracast / дзеркалювання екрана |
| com.google.android.apps.tv.dreamx | Заставка Google (ambient mode), ~160 МБ під час роботи. Замінена на «Дім: годинник» (`ua.home.launcher/.ClockDream`) |

Перед вимкненням dreamx заставку переключено: `settings put secure screensaver_components ua.home.launcher/.ClockDream` (було `com.google.android.apps.tv.dreamx/.service.Backdrop`). Якщо повертаєте dreamx, поверніть і цей рядок.
Після `adb reboot` (2026-10-23 00:30, завантаження ~32 с): усі 21 пакет чотирьох партій лишились disabled, «Дім» відкрився сам, служба кнопки Home і заставка на місці. MemAvailable через ~1,5 хв після старту: 931 МБ (до партії 4 найкраще було 923 МБ).
Пульт, HDMI, PS5 після перезавантаження: _перевіряє користувач_.

## Анімації — 2026-10-23

`window_animation_scale`, `transition_animation_scale`, `animator_duration_scale`: 0.5 (було 1.0 / 1.0 / не задано). Повернути: `adb shell settings put global <ключ> 1`.
