# Пример автотестов REST API Яндекс Диска

Небольшой проект на Java 17, JUnit 5 и Maven. Он проверяет HTTP-методы `GET`, `POST`, `PUT` и `DELETE` для ресурсов Яндекс Диска. По умолчанию тесты обращаются только к локальному HTTP-серверу и не требуют OAuth-токена. Отдельный тест можно запустить против `https://cloud-api.yandex.net` с токеном **выделенной тестовой учётной записи**.

## Что проверяется

| Метод | Запрос | Локальная проверка | Реальный API |
| --- | --- | --- | --- |
| GET | `/v1/disk/resources?path=...` | URL, OAuth-заголовок, ответ 200 | Метаданные созданной папки |
| PUT | `/v1/disk/resources?path=...` | URL, ответ 201 | Создание двух папок |
| POST | `/v1/disk/resources/copy?from=...&path=...` | Оба параметра, ответ 201 | Копирование пустой папки |
| DELETE | `/v1/disk/resources?path=...&permanently=true` | Путь и флаг, ответ 204 | Удаление копии и очистка папки теста |

Локальные тесты проверяют формирование запросов клиента, а не поведение сервиса Яндекса. Реальный тест проверяет последовательность операций и допускает асинхронный ответ `202` для копирования и удаления.

## Запуск

Нужны JDK 17+ и Maven 3.9+.

```bash
mvn test
```

Ожидаемый результат без токена: четыре локальных теста проходят; `DiskApiLiveTest` пропускается. Этот же набор запускается в GitHub Actions на каждом push и pull request.

### Проверка реального Диска

Получите OAuth-токен **для отдельной тестовой учётной записи** с правом работы с её Диском. Не используйте личную учётную запись. Передайте токен через переменную окружения `YANDEX_DISK_TOKEN`, затем включите живой тест:

```bash
printf 'OAuth token: '
read -s YANDEX_DISK_TOKEN
printf '\n'
export YANDEX_DISK_TOKEN
export YANDEX_DISK_RUN_LIVE=true
mvn test
unset YANDEX_DISK_TOKEN YANDEX_DISK_RUN_LIVE
```

Токен не нужен для GitHub Actions и не должен попадать в репозиторий, логи или командную историю. В живом тесте используется уникальная папка `disk:/yandex-disk-api-tests-<UUID>`; после проверки она удаляется в блоке `finally`. `DELETE` с `permanently=true` удаляет её без корзины, поэтому запускайте тест только на выделенном тестовом Диске.

## Структура

- `src/main/java/.../DiskApiClient.java` — минимальный HTTP-клиент на стандартном `java.net.http`.
- `src/test/java/.../DiskApiClientTest.java` — четыре локальных теста с HTTP-сервером JDK.
- `src/test/java/.../DiskApiLiveTest.java` — один включаемый вручную сценарий против сервиса.
- `.github/workflows/tests.yml` — CI без токена.

## Документация

- [REST API Яндекс Диска](https://yandex.ru/dev/disk/rest/)
- [Полигон](https://yandex.ru/dev/disk/poligon/)
- [Описание API](https://yandex.ru/dev/disk/api/concepts/about-docpage/)
- [JUnit 5](https://junit.org/junit5/)
