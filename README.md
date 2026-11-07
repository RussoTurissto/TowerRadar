# TowerRadar

An independent Android application for showing nearby telecommunications masts and potentially mobile-network installations on a live map.

**Version:** 0.4.0 (prototype). **Language:** Russian. **Platform:** Android 8.0+.

## Features

- GPS-based search with user location marker and updates while the app is open.
- MapLibre map with a built-in raster style, availability probing and manual map provider switching: OSM France, OpenStreetMap, OSM Deutschland, and OSM HOT.
- Real-time phone azimuth from the rotation vector sensor with magnetic declination correction, displayed as an arrow on the GPS marker.
- Optional OpenCellID key entry for estimated positions of logical radio cells near the center, not physical masts.
- Expanded Overpass API query with mast/antenna/operator tags, plus server failover: VK Maps Overpass, Private.coffee, and FOSSGIS.
- Radius selector: 1, 3, 5, 10, 20, 50 km.
- Filtering by reported operator, map pin detail panel, OSM source link.
- Blue markers indicate OSM tags mentioning mobile communication; orange markers indicate unconfirmed telecom structures.
- Cached result for recent searches, manual refresh, network error messages.
- No location history collection and no background location tracking.

**Data limitations:** OpenStreetMap is crowd-sourced and incomplete. A mapped mast is not necessarily a mobile cellular base station. No frequency, live serving-cell identity, precise radio cell estimate, or operator ownership is inferred when the tags do not specify it. An empty region does not prove there are no towers. On manual refresh, approximate search coordinates are sent to the public Overpass endpoint.

## Development

Open the repository in Android Studio with JDK 17. Android Gradle Plugin 8.11.1, Gradle 8.13, Kotlin 2.2.21, compileSdk 35, MapLibre Android 13.7.1.

The GitHub Actions workflow on push to main runs JVM unit tests, Android lint, debug APK assembly, signature verification and checksum generation. The resulting **TowerRadar-debug-APK** is available under the successful run's **Artifacts** section.

Debug APKs are signed with GitHub Actions' temporary debug signing key. Do **not** rely on the signature staying identical across builds or devices; Android app upgrades may require uninstalling the previous debug build. A stable, user-owned release keystore is a later milestone and must never be checked into the repository.

## Sources and licenses

Map tiles: https://www.openstreetmap.fr/usage/ and https://operations.osmfoundation.org/policies/tiles/
Optional cell data: https://opencellid.org/ (CC BY-SA 4.0; user-owned API key required)
Map data and telecom object metadata: https://www.openstreetmap.org/copyright
Query service: https://wiki.openstreetmap.org/wiki/Overpass_API
Map renderer: https://maplibre.org/

OpenStreetMap data © OpenStreetMap contributors, licensed under ODbL. Provider terms and rate limits apply.

### v0.2.0 limitations

Map tiles require an available external tile server; on some networks all public servers may be blocked. The app switches among independent sources, but no online-only solution can guarantee availability without VPN. A logical cell position from OpenCellID is not proof of a physical cellular tower. Locations from OpenStreetMap are likewise crowd-sourced, incomplete, and may be imprecise. The OpenCellID key is stored in app-private preferences, backup is disabled, and requests go directly to OpenCellID (not via Github). Confirm map access on a real device and network.

### v0.4.0 — выбор карты и русские подписи

- Кнопка «Карта» заменена на выпадающий список с автоматическим выбором и четырьмя OSM-подложками.
- Добавлены **Яндекс Карты (ru_RU)** и **2ГИС** через официальные raster Tiles API. Требуется ваш собственный ключ соответствующего провайдера, сохраняемый на телефоне; сторонние API-тарифы/ограничения зависят от поставщиков.
- Обычные OSM-подложки подписывают объекты на местных языках: в русскоязычных регионах часто на русском, но за их пределами русский не гарантирован.
- Выбор карты сохраняется между запусками; при недоступности сервера остаётся ранее доступная подложка; для обновления ключа удерживайте выбранный пункт списка.
- Для Яндекса необходимы брендирование и соблюдение [условий Tiles API](https://yandex.ru/maps-api/docs/tiles-api/index.html). На экране предусмотрена кликабельная ссылка на Яндекс Карты; перед публичным распространением следует проверить соответствие официальным требованиям логотипа.


## TowerRadar 0.4.0 — Khaki Modern, GPS и подключённая сота

**Возможности Android 8+**:
- Три режима темы: **Системная / Дневная / Ночная**, в минималистичных настройках ⚙.
- Элементы экрана в современной палитре хаки; карта OSM приглушается в тёмной теме. Лицензионные карты сохраняют собственный стиль.
- Большой указатель направления, цифровой азимут и полупрозрачный сектор (можно отключить).
- Свежие координаты через Android GPS/NETWORK + Google Fused Location Provider при наличии сервисов Google (GNSS, Wi-Fi, cell). Без Google работают GPS/NETWORK. Wi-Fi RTT требует совместимой и подготовленной инфраструктуры, здесь не реализован.
- Круг реальной горизонтальной погрешности Android, предупреждение о низкой точности.
- Ручной центр карты, ввод географических координат, сохранение исправленной точки и возврат к GPS (меню настроек либо длительное нажатие ◎).
- Определение обслуживающей соты Android Telephony API: dBm, RSRP, RSRQ, SINR (по доступности), пятиступенчатая шкала на основе системного уровня Android, регулярное обновление.
- Выбор SIM; по умолчанию отслеживается SIM для мобильного интернета.
- Пунктир от пользовательской точки к оценке позиции обслуживающей соты, только при совпадении MCC/MNC/TAC(LAC)/Cell ID/radio с OpenCellID. Это **не подтверждённая физическая вышка**. Если нет ключа/записи в базе, линия не рисуется и это объясняется на экране.
- Выпадающий список карт: авто, четыре OSM-подложки, Яндекс Tiles API и 2ГИС Raster Tiles API. Русские подписи на Яндекс/2ГИС зависят от активированного личного API-ключа. Переход к кабинету Яндекс ID запускается в браузере; автоматической выдачи ключей при логине нет.

### Ограничения и проверка

Для чтения точной обслуживающей соты требуются доступ к точной геопозиции и разрешение READ_PHONE_STATE. Android и модем могут скрывать Cell ID и некоторые метрики; в режиме 5G NSA обслуживающая сота может отображаться как LTE. Системное местоположение, Wi-Fi и спутники могут давать разную погрешность в зависимости от условий. Частота радиообновлений зависит от ОС. Доступ к геопозиции и радио используется только пока приложение открыто.

Без личного ключа OpenCellID координаты соты не запрашиваются с сервера. Поиск вышек по Overpass передаёт выбранные координаты на сервер данных. Все личные API-ключи сохраняются только локально на устройстве; ограничения использования и тарифы определяют поставщики. Ключи пока хранятся в Android SharedPreferences без отдельного аппаратного шифрования — используйте ключи, ограниченные у поставщика.

GitHub Actions проверяет unit-тесты, Android Lint, сборку и криптографическую подпись debug APK. Геопозицию, показания реального модема и визуальный контраст экранов необходимо дополнительно проверять на пользовательском телефоне. Debug APK разных сборок может подписываться разными временными ключами и требовать удаления старой версии.

**Лицензия Яндекса:** требуется официальное брендирование Tiles API, включая логотип по текущим правилам. Перед публичным или коммерческим распространением проверьте условия Яндекса и других поставщиков карт.


## TowerRadar v0.5.0 — карта на весь экран и подключение Яндекс Карт

- Карта занимает весь экран, над ней лишь компактный блок компаса/GPS, настройки, полноэкранный режим и небольшие кнопки геопозиции/поиска.
- Снизу — компактная панель оператора и сигнала (84dp). Свайп вверх или касание «Подробности» раскрывает панель (~73% полезной высоты) с поиском, радиусом, фильтрами, выбором подложки, ручной геопозицией, дополнительными сотами и информацией о подключённой соте.
- Полноэкранный режим убирает панель и боковые кнопки, но сохраняет компас/настройки и обязательную атрибуцию картографического провайдера.
- Кнопка «Яндекс ID · подключить карты» запускает понятный мастер: пользователь входит в официальный кабинет Яндекса через системный браузер, подключает продукт Tiles API, копирует выданный ключ и нажимает «Вставить ключ». Чтение буфера производится **только по нажатию**. API-ключ карты проверяется доступностью тайла.
- **Автоматическая выдача API-ключа по одному входу через Яндекс ID не поддерживается**: у Яндекса доступ к Tiles API включается отдельно, с согласием на тариф и активацией. Приложение не собирает пароли и не обходит процедуру выдачи ключа.
- При необходимости ключ можно ввести вручную. Возможность получения карты зависит от активированного тарифа, доступности сервера, условий лицензии и соблюдения требований к логотипу Яндекса.
- Сохранены день/ночь/системная тема, выделение обслуживающей соты, пятиступенчатая шкала сигнала, круг погрешности GPS, ручная точка и сектор направления.


## TowerRadar v0.6.0 — визуальный контроль направления и единый интерфейс

- Все собственные диалоги и меню приложения приведены к единому стилю **Khaki Modern**, с согласованными иконками, фоном, цветом текста и кнопками. Системные разрешения Android не изменяются.
- В меню геопозиции доступен новый режим **«Установить точку перекрестием»**: неподвижный яркий прицел над картой, панорамирование и масштабирование карты под ним, касание нужной точки для быстрого наведения, текущие координаты и действия «Установить точку» / «Отмена». Ручная координата сохраняется; отмена не изменяет сохранённое местоположение.
- Вверху действует **WindowInsets** для системной строки состояния и вырезов камеры; снизу также учитываются системные навигационные панели. Карта остаётся полноэкранной, а интерактивные элементы не перекрывают системные значки.
- Сектор направления стал бирюзовым, получил бело-синюю контрастную обводку и яркий центральный луч. Его длина пересчитывается по видимому масштабу карты: после отдаления он не исчезает.
- В меню настроек выбирается интенсивность сектора: **Обычный / Яркий / Максимальный** (по умолчанию — «Яркий»). Оранжевый пунктир к обслуживающей соте остаётся отдельным.
- Все варианты остаются **предварительными версиями**. Стабильный релиз 1.0.0 публикуется исключительно после отдельного явного подтверждения пользователя, независимо от состояния автоматических тестов.

### Проверка
Сборка Android + unit-тесты + Android Lint и проверка подписи APK выполняются GitHub Actions. Реальное поведение системной строки состояния, цветовой контраст и точность ручного выбора требуют дополнительной проверки на устройстве после установки.


## TowerRadar v0.7.0 — Google Tiles, настройки направления и проверка API

- **Google Map Tiles API** через официальные сеансы (HTTPS POST createSession, GET 2D tiles). В списке подложек доступны обычная Google Карта (roadmap), Google Спутник (satellite) и Google Гибрид (satellite + layerRoadmap, overlay=false). Для работы необходим собственный API-ключ Google Cloud с включённым Map Tiles API и Billing. Сеансы повторно создаются по истечении срока действия; ключ остаётся на телефоне. Google Maps и запрошенная с сервера атрибуция текущей области показываются поверх карты. Соблюдайте условия Google Maps Platform, включая ограничения на использование и кеширование картографических данных.
- **Подключения API** в настройках: Яндекс, 2ГИС, Google, OpenCellID. Сохранённый ключ распознаётся сразу, но зелёная галочка появляется **только после реального успешного запроса**. Можно повторно проверить подключение; сообщения различают HTTP 400, 401/403, 404, 429, 5xx и сетевые сбои. Яндекс и 2ГИС используют официальные Raster Tiles/Tiles API. Если карта недоступна, приложение предлагает OSM.
- **Геопозиция для расчёта расстояния**: при ручном выборе точки все расстояния до вышек и примерной позиции обслуживающей соты считаются от этой точки; без ручного выбора — от актуального GPS/Wi-Fi, а не от случайно перемещённого центра карты.
- **Индикатор направления**: компактный полупрозрачный радарный веер, компасное кольцо вокруг точки и бирюзовый **пунктир вместо стрелки**, мягкие тонкие контуры. Регулировка яркости луча и сектора **по отдельности** в процентах от 0 до 100, сохраняется между запусками. Линия обслуживающей соты остаётся оранжевой.
- **Распространение**: версия 0.7.0 остаётся **pre-release** и не является стабильной 1.0.0. Статус stable публикуется только после отдельного явного разрешения пользователя.

Автоматическая проверка сборки, unit-тесты, lint и APK v2 signature выполняются в GitHub Actions. Для проверки реального подключения каждому сервису нужен личный ключ пользователя; ключи не встроены в APK и не отправляются разработчику/в GitHub.
