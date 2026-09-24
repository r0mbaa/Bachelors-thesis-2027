# planner-engine — алгоритмы планирования

Чистая Java-библиотека, в которой находится всё, что вычисляет планировщик. Сервис [`planner`](../planner) только публикует её по REST (§12.4), а стенд [`research`](../research) вызывает напрямую, без веб-сервера и БД. Поэтому JMH меряет сам алгоритм, а не HTTP-обвязку (NFR-E-04).

## Реестр стратегий

Каждое семейство реализует свой интерфейс стратегии. Реализации помечаются `@Component`, и контейнер Spring собирает их в `Map<String, …Strategy>` автоматически: ключ карты — имя бина (§12.3). Сервис `planner` и стенд `research` получают полный список стратегий одинаково, стенд — через лёгкий контекст Spring без Boot и веб-сервера. Добавить алгоритм — значит добавить класс, ядро не меняется (NFR-M-01).

| Пакет | Модуль §7 | Каталог методов |
| --- | --- | --- |
| [`distance`](src/main/java/io/github/r0mbaa/wms/engine/distance) | M8 Матрица расстояний | §9.1, вспомогательные |
| [`routing`](src/main/java/io/github/r0mbaa/wms/engine/routing) | M8 Маршрутизация | §9.1 |
| [`batching`](src/main/java/io/github/r0mbaa/wms/engine/batching) | M7 Батчинг | §9.2 |
| [`dispatch`](src/main/java/io/github/r0mbaa/wms/engine/dispatch) | M9 Диспетчеризация | §9.3 |
| [`allocation`](src/main/java/io/github/r0mbaa/wms/engine/allocation) | M6 Аллокация | §8.4 |
| [`slotting`](src/main/java/io/github/r0mbaa/wms/engine/slotting) | M12 Слоттинг и аналитика | §9.4 |

**Отступление от §12.1.** Спецификация перечисляет четыре пакета стратегий: `routing/`, `batching/`, `dispatch/`, `allocation/`. Пакет `slotting/` добавлен под M12, потому что §12.4 относит `POST /api/v1/analytics/slotting` к контракту именно планировщика, а §9.4 описывает слоттинг как такое же семейство взаимозаменяемых методов.

Метод `supports(LayoutClass)` вместе с классификатором топологии из `layout` (FR-M15-08) позволяет системе сообщить, какие алгоритмы применимы к построенной пользователем планировке.

**Библиотеки:** JGraphT (Дейкстра, Флойд–Уоршелл, эвристики TSP, `HeldKarpTSP`), OR-Tools, SPMF, Smile или Tribuo. Подключаются по мере появления алгоритмов, которым они нужны.
