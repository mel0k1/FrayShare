<p align="center"><img src="docs/brand/fray.jpg" width="128" alt="Fray"></p>

<h1 align="center">FrayShare</h1>

<p align="center">
  <a href="https://github.com/mel0k1/FrayShare/actions/workflows/build.yml"><img src="https://github.com/mel0k1/FrayShare/actions/workflows/build.yml/badge.svg" alt="build"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="MIT"></a>
</p>

Перенос фото, видео и приложений между Android-телефонами по Wi-Fi. Отдельная вкладка «Полный перенос» собирает всё разом: контакты, медиатеку, APK и загрузки — для переезда на новый телефон.

## Как это работает

- Оба телефона подключены к одной сети Wi-Fi, либо один раздаёт точку доступа.
- Fray ищет получателя UDP-объявлениями в локальной сети, отвечающее устройство возвращает свой адрес.
- Передача идёт по TCP (порт 47470): манифест, затем файлы по очереди, подтверждение после каждого.
- Полученные файлы сохраняются в `Download/Fray`.

## Возможности

- Фото и видео из медиатеки, оригиналы без сжатия.
- Установленные приложения — отправка APK, кроме системных.
- Контакты одним файлом vCard, открывается любым менеджером контактов.
- Файлы из папки загрузок на Android 10 и новее.
- Вкладка «Полный перенос»: одна кнопка на старом телефоне, одна на новом.
- История передач с размером и временем.
- Material 3 Expressive: `MaterialExpressiveTheme`, `MotionScheme.expressive()`, крупные радиусы и пружинная анимация.

## Ограничения

- SMS, журнал звонков и системные настройки не переносятся.
- Переносятся APK приложений, но не их данные и не переписки — для этого у Google есть резервное копирование.
- На Android 9 и старше поиск файлов из загрузок недоступен.

## Сборка

Нужен JDK 17.

```bash
./gradlew assembleDebug
```

Готовый APK: `app/build/outputs/apk/debug/app-debug.apk`.

Тесты:

```bash
./gradlew testDebugUnitTest
```

## Стек

Kotlin (встроенная поддержка в AGP 9), Jetpack Compose, `androidx.compose.material3:material3:1.5.0`, Kotlin coroutines, AGP 9.5.1, Gradle 9.9.1. Сборка и тесты прогоняются в GitHub Actions на каждый пуш.

## Дорожная карта

- QR-код вместо UDP-поиска.
- Шифрование канала передачи.
- Возобновление прерванной передачи.
- Передача по Wi-Fi Direct без общей сети.

## Лицензия

[MIT](LICENSE)
