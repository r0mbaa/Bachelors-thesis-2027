# wms — программный модуль управления заданиями и маршрутизацией

Gradle multi-project. Структура соответствует §12.1 [спецификации](../Спецификация%20по%20диплому.md); деление верхнего уровня проведено **по профилю нагрузки и модели состояния**, а не по предметным сущностям (обоснование — §12.2).

| Модуль | Состояние | Профиль | Содержимое |
| --- | --- | --- | --- |
| [`shared/`](shared) | — | — | DTO, доменные типы-значения, контракты API |
| [`core/`](core) | Stateful, владеет БД | I/O-bound, короткие транзакции | Учёт, конструктор, задания, документы |
| [`planner/`](planner) | Stateless | CPU-bound, секунды на запрос | Маршрутизация, батчинг, диспетчеризация, слоттинг |
| [`research/`](research) | — | CLI, не сервис | Генераторы, симулятор, прогоны, JMH |
| [`analysis/`](analysis) | — | Офлайн, вне системы | Python: статистика и графики по CSV-выгрузке |
| [`web/`](web) | — | Браузер | React: конструктор 2D, сцена 3D, монитор |
| [`terminal/`](terminal) | — | Браузер, мобильный | PWA-терминал сборщика |

## Раскладка функциональных модулей M1–M15

| M | Модуль спецификации | Где реализуется |
| --- | --- | --- |
| M1 | Топология склада и адресное хранение | [`core/topology`](core/topology) |
| M2 | Номенклатура | [`core/catalog`](core/catalog) |
| M3 | Приёмка и размещение | [`core/receiving`](core/receiving) |
| M4 | Остатки, движения, инвентаризация | [`core/inventory`](core/inventory) |
| M5 | Заказы и волны | [`core/order`](core/order) |
| M6 | Аллокация и резервирование | [`core/allocation`](core/allocation) + [`planner/strategies/allocation`](planner/strategies/allocation) |
| M7 | Батчинг | [`planner/strategies/batching`](planner/strategies/batching) |
| M8 | Маршрутизация | [`planner/strategies/routing`](planner/strategies/routing) |
| M9 | Диспетчеризация заданий | [`core/task`](core/task) + [`planner/strategies/dispatch`](planner/strategies/dispatch) |
| M10 | Мобильный терминал сборщика | [`terminal/`](terminal) |
| M11 | Отгрузка и документы | [`core/shipping`](core/shipping) |
| M12 | Слоттинг и аналитика (ML) | [`planner/strategies/slotting`](planner/strategies/slotting) |
| M13 | Администрирование | [`core/admin`](core/admin) |
| M14 | Имитационный стенд и эксперименты | [`research/`](research) |
| M15 | Конструктор склада и 3D | [`core/layout`](core/layout) + [`web/`](web) |

**M6 и M9 намеренно разрезаны между сервисами.** Состояние (записи резервов, записи заданий) принадлежит `core`, алгоритм выбора — `planner`. Это прямое следствие границы из §12.2: она проведена по профилю нагрузки, поэтому предметный модуль может её пересекать.

## Что здесь пока отсутствует

Это разметка структуры, а не начало реализации: нет `settings.gradle.kts`, `build.gradle.kts`, `docker-compose.yml` и каталогов `src/main/java`. Базовое имя Java-пакета не выбрано — спецификация его не фиксирует.
