# LFPT-0967: Feature короткая ссылка VamosPlan — вести на список турниров с UTM

Задача [#967](https://github.com/UstinKO/padel-core-service/issues/967).

Публичный `GET /vamosplan` возвращает HTTP 302 на главную со списком турниров.

```text
https://1-padel.com/?utm_source=vamosplan&utm_medium=referral&utm_campaign=partner_listing
```

Путь доступен гостям и вошедшим посетителям. Параметры запроса не меняют фиксированное назначение. Используются существующие правила доступа к главной и согласия на аналитику.

Сценарии приёмки входят в `MarketingRedirectControllerTest`. После выпуска HTTP-проверка без сессии подтверждает точное назначение 302 и ответ 200 от главной.
