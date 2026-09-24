# core — учётное ядро

Spring Boot, Java 25. Stateful: владеет PostgreSQL. Профиль — I/O-bound, короткие транзакции.

Отказ `core` останавливает работу склада, поэтому после стабилизации код здесь меняется редко (§12.2).

Геометрию (ячейки, граф, валидацию планировки) `core` не вычисляет сам, а получает из библиотеки [`layout`](../layout).

| Пакет | Модуль §7 | Требования |
| --- | --- | --- |
| [`topology/`](src/main/java/io/github/r0mbaa/wms/core/topology) | M1 Топология и адресное хранение | FR-M1-01 .. 14 |
| [`catalog/`](src/main/java/io/github/r0mbaa/wms/core/catalog) | M2 Номенклатура | FR-M2-01 .. 06 |
| [`receiving/`](src/main/java/io/github/r0mbaa/wms/core/receiving) | M3 Приёмка и размещение | FR-M3-01 .. 07 |
| [`inventory/`](src/main/java/io/github/r0mbaa/wms/core/inventory) | M4 Остатки, движения, инвентаризация | FR-M4-01 .. 10 |
| [`order/`](src/main/java/io/github/r0mbaa/wms/core/order) | M5 Заказы и волны | FR-M5-01 .. 08 |
| [`allocation/`](src/main/java/io/github/r0mbaa/wms/core/allocation) | M6 Аллокация и резервирование | FR-M6-01 .. 06 |
| [`task/`](src/main/java/io/github/r0mbaa/wms/core/task) | M9 Задания и события терминала | FR-M9-01 .. 09 |
| [`shipping/`](src/main/java/io/github/r0mbaa/wms/core/shipping) | M11 Отгрузка и документы | FR-M11-01 .. 07 |
| [`layout/`](src/main/java/io/github/r0mbaa/wms/core/layout) | M15 Конструктор, серверная часть | FR-M15-01 .. 31 |
| [`admin/`](src/main/java/io/github/r0mbaa/wms/core/admin) | M13 Администрирование | FR-M13-01 .. 05 |

## Транзакционная целостность

Основную работу выполняет СУБД, фреймворк только управляет границами транзакций (§12.2): CHECK-ограничения против отрицательных остатков, `@Version` для конкурентной аллокации, `PESSIMISTIC_WRITE` в детерминированном порядке строк при резервировании волны, уникальный индекс для идемпотентности событий терминала, отзыв прав `UPDATE`/`DELETE` на журнал движений.

**Порт по умолчанию:** 8080.
