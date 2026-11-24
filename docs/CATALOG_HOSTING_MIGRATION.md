# Публичный каталог и приватный исходный репозиторий

## Цель и схема

Исходный репозиторий `mellefresh13-tech/radio-world-auto` содержит Android-приложение, collector, API, тесты, workflows и документацию. Его планируется перевести в private отдельно, только после проверки миграции.

Публичные данные вынесены в отдельный data-only репозиторий `mellefresh13-tech/radio-world-catalog`. Он предназначен только для готового каталога и публичной статической диагностики. Android-клиент читает данные без авторизации через GitHub Raw.

```text
radio-world-auto (private после отдельного переключения)
  source + collector + tests + workflows
            |
            | Catalog snapshot: build + validate
            v
  build job (read-only)
            |
            | publication artifact
            v
  publisher job (revalidates bundle)
  CATALOG_PUBLISH_TOKEN доступен только push step
            |
            v
radio-world-catalog (public) / catalog-data
  data/stations.json
  data/catalog-manifest.json
  data/catalog-delta.json
  data/catalog-delta-history.json
  data/radio.db
  debug/...
            |
            v
Android app (без токена)
```

## Публичные endpoints

Все адреса используют ветку `catalog-data` целевого репозитория:

- [stations.json](https://raw.githubusercontent.com/mellefresh13-tech/radio-world-catalog/catalog-data/data/stations.json)
- [catalog-manifest.json](https://raw.githubusercontent.com/mellefresh13-tech/radio-world-catalog/catalog-data/data/catalog-manifest.json)
- [catalog-delta.json](https://raw.githubusercontent.com/mellefresh13-tech/radio-world-catalog/catalog-data/data/catalog-delta.json)
- [catalog-delta-history.json](https://raw.githubusercontent.com/mellefresh13-tech/radio-world-catalog/catalog-data/data/catalog-delta-history.json)
- [radio.db](https://raw.githubusercontent.com/mellefresh13-tech/radio-world-catalog/catalog-data/data/radio.db)

GitHub Pages не требуется. GitHub Raw обслуживает статические файлы по HTTPS. Публичный каталог можно скачать или скопировать — это ожидаемо, поскольку Android-клиент должен читать его без учётных данных.

## Публикация и безопасность

В исходном репозитории должен быть Actions secret `CATALOG_PUBLISH_TOKEN`. Это fine-grained PAT со следующими ограничениями:

- Resource owner: `mellefresh13-tech`;
- доступ только к `radio-world-catalog`;
- Repository permission: `Contents: Read and write`;
- ограниченный срок действия и запланированная ротация.

Токен нельзя добавлять в исходный код, Android APK, артефакты, manifest или логи. Android не использует токен.

Workflow `Catalog snapshot` собирает и публикует данные только из `main`:
1. Build job собирает каталог, SQLite, manifest, delta, историю и debug; имеет только read permissions и не получает publish token.
2. Для расчёта delta предыдущая публичная версия читается анонимно из `radio-world-catalog/catalog-data`.
3. Shrink guard сохраняет порог 25% и требует ручного `allow_catalog_shrink=true` для подтверждённого намеренного сокращения.
4. Publisher job скачивает bundle, повторно проверяет JSON/SQLite и shrink guard.
5. PAT доступен только финальному шагу push. Публикуется отдельный orphan snapshot в `radio-world-catalog/catalog-data`; исходники приложения в целевой репозиторий не попадают.

Публикация не запускается из pull request или feature-веток. Секрет не передаётся build job, тестам или другим workflow.

## Изменённые компоненты

- Android: четыре Raw URL в `RadioApiClient.kt`.
- Android tests: canonical URL policy проверяет новый репозиторий.
- `tools/build_catalog_delta.py`: получает предыдущие станции, manifest и delta history из fetched ref целевого репозитория.
- `tools/check_catalog.py`, catalog health workflow и live metadata debugger: читают каталог из нового репозитория.
- API: значение по умолчанию `CATALOG_DB_URL` указывает на новую публичную SQLite.
- README, Android/API/deploy README и архитектура описывают новый endpoint.

## Проверка после merge

1. Убедиться, что secret `CATALOG_PUBLISH_TOKEN` добавлен в `radio-world-auto`, а PAT имеет только указанные выше права.
2. Первую публикацию запустить вручную через **Catalog snapshot** на `main` с `allow_catalog_shrink=true`: в новом публичном репозитории ещё нет предыдущего manifest, поэтому это осознанный bootstrap-обход shrink guard. Перед запуском проверить candidate/artifact и убедиться, что сокращение не вызвано ошибкой сборщика. Последующие запуски выполнять с `allow_catalog_shrink=false` (по умолчанию).
3. Убедиться, что ветка `catalog-data` появилась в `radio-world-catalog` и содержит пять файлов данных, `.nojekyll` и debug-файлы.
4. Открыть каждый публичный URL без авторизации; проверить HTTP 200 и корректный JSON/SQLite.
5. Проверить manifest: version, station_count, playable_station_count, delta и delta history; сравнить количество станций с предыдущей публикацией.
6. Запустить **Catalog health check**, проверить несколько станций, включая curated Radio ZET. При необходимости отдельно выполнить live metadata debugger.
7. Собрать APK из нового `main`. Проверить чистую установку, первоначальную загрузку полного каталога, обычное обновление, delta update, full fallback и работу локального каталога без сети.
8. Проверить потерю сети и восстановление подключения после возвращения интернета.
9. Только после успешной публикации и тестирования APK перевести `radio-world-auto` в private.
10. После смены visibility повторно проверить все Raw URL и холодный запуск приложения. `radio-world-catalog` оставить public.

## Важные различия

- Каталог — список станций и метаданные. Его локальная копия позволяет просматривать станции без сети.
- Радиоэфир — аудиопоток серверов станций; для прослушивания всегда нужен интернет.
- Если PAT истечёт или будет отозван, новые публикации остановятся, но последний опубликованный каталог останется доступным.
- GitHub Raw — статический источник без SLA-гарантии; локальный кэш снижает зависимость при последующих запусках, но первая загрузка и обновления требуют сети.

## Статус

Этот PR переносит конфигурацию и ссылки, но не запускает публикацию и не меняет visibility исходного репозитория. После merge обязательны первая публикация из Actions и ручная проверка APK. Перевод `radio-world-auto` в private — отдельное действие после QA.
