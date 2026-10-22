# Прошивка і регіон ru (2026-10-23)

## Чому регіон ru

Регіон записано на заводі, у розділі factory: `vendor.mitv.factory.region=ru`, `ro.boot.region=ru`, `persist.tv.client_sku=ru`. Налаштування користувача українські (`persist.sys.locale=uk-UA`, `persist.sys.user_region=ua`, `service_region=ua`), але на прошивку вони не впливають.

Модель-варіант задає регіон: базовий образ `MiTV-MOOR0 / venom` (`ro.product.odm.model`), а ТВ представляється як `MiTV-MOOR2 / flipped` (`ro.product.model`, `ro.product.name`).
За відкритим довідником моделей ([MobileModels](https://github.com/KHwang9883/MobileModels/blob/master/brands/mitv_global_en.md)) Xiaomi TV A 43 2025 4K має коди `L43MA-AURU` (Росія) і `L43MA-AME` (Україна / Близький Схід). Отже, найімовірніше, це ТВ для ринку РФ/СНД, ввезений в Україну. Точний код написано на наклейці на задній панелі; перевірити: там `L43MA-AURU` чи `L43MA-AME`.

## Чому немає оновлення

- Прошивку оновлює Google, а не Xiaomi: `com.google.android.gms/.update.SystemUpdatePanoActivity`, A/B через `update_engine`. Google шукає оновлення за відбитком `Xiaomi/flipped/venom:11/RTM5.220609.003/1917`.
- 2026-10-23 00:04 ручна перевірка: `Checkin Succeeded: https://android.googleapis.com/checkin`, але оновлення не запропоновано (лог `diag/2026-10-22/ota_check_log.txt`, локально). Тобто для `flipped` 1917 сервер нічого новішого не роздає.
- Збірка `RTM5.220609.003.2674` існує на іншому MOOR2 ([cleanyourtv #53](https://github.com/Gathin23/cleanyourtv/issues/53), 2026-10-18; там же згадано другий такий ТВ). Регіон там не вказано. Можливі пояснення: новіші ТВ виходять із заводу вже з 2674, або Google розкочує оновлення поступово чи за країною. З наявних даних не розрізнити.
- Служби Xiaomi `com.xiaomi.mitv.updateservice` (2.3.1, оновлена 2026-09-28) і `com.mitv.upgrader` оновлюють додатки Xiaomi і стан оновлення, а не саму прошивку.
- Прошивка Xiaomi перетворює будильники-пробудження на звичайні (`Change Wakeup Alarm type to non Wakeup type`), зокрема для updateservice. Тож версія з Habr, ніби updateservice будить ТВ уночі, на цій прошивці не підтверджується.

## Варіанти

1. **Чекати офіційне оновлення через Google.** Безпечно, але невідомо, чи воно колись буде для `flipped`. Перевіряти: Налаштування → Система → Про пристрій → Оновлення системи, або `adb shell am start -n com.google.android.gms/.update.SystemUpdatePanoActivity`. updateservice і upgrader не вимикати.
2. **Підтримка Xiaomi / продавець.** Повторювані kernel panic (2026-10-19 і 2026-10-22, записані в `persist.sys.boot.reason.history`) — це гарантійна несправність. Можна попросити сервіс оновити ТВ до 2674 або замінити його. Якщо це варіант AURU, гарантію в Україні дає продавець, а не офіційна мережа Xiaomi.
3. **Ставити прошивку вручну: не радимо.** Завантажувач заблоковано (`ro.boot.vbmeta.device_state=locked`, `verifiedbootstate=green`), образ має бути підписаний Xiaomi саме для цього варіанта. Офіційних файлів для USB-оновлення цієї моделі Xiaomi не публікує, а сторонні сайти (наприклад gofirmware) неперевірені. Прошивка з іншого регіону (`MOOR0`, EU) може не встати або «окирпичити» ТВ.
4. **Скидання до заводських налаштувань** версію прошивки не змінює. Перед ним треба увімкнути всі вимкнені пакети (див. [debloat.md](debloat.md)), Google TV launcher і setupwraith.

## Чи можна змінити регіон самостійно (2026-10-23): ні

- `ro.boot.region=ru` передає завантажувач під час старту (`androidboot.*`). Властивості `ro.*` змінювати не можна в принципі.
- `vendor.mitv.factory.region` береться з розділу `/vendor/factory`, а він змонтований лише для читання через dm-verity (`/dev/block/dm-9 ... ro`). Shell не має доступу навіть на читання `/mnt/vendor/factory`.
- `persist.tv.client_sku` перезаписується з `ro.boot.region` на кожному завантаженні (`/vendor/etc/init/init.fusion.rc`: `on property:ro.boot.region=*` → `setprop persist.tv.client_sku ${ro.boot.region}`). До того ж це `system_prop`, а shell (`u:r:shell:s0`) таке змінювати не може.
- Без root нічого з цього не змінити. А root неможливий, поки завантажувач заблоковано (`vbmeta.device_state=locked`). Офіційного розблокування для ТВ Xiaomi немає. Навіть якби воно було, розблокування стерло б дані і, найімовірніше, зламало б Widevine L1 (Netflix і стрімінги в HD).
- У заводському меню (`com.duokan.factorytest`) теоретично може бути вибір регіону, але це сервісний інструмент. У cleanyourtv описано, як після відкриття заводського меню на іншому ТВ перестали працювати кнопка Input і HDMI-CEC. Не відкривати.
- Навіть якщо змінити регіон, оновлення не гарантоване: ТВ отримав би інший відбиток збірки, і невідомо, чи для нього є 2674. Можуть також змінитися налаштування цифрового ТБ і список сертифікованих додатків.
- Офіційно змінити регіон може лише сервіс Xiaomi або продавець.
