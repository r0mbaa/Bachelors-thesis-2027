# research — имитационный стенд и модуль экспериментов

Java, запускается как CLI, **не сервис**. Зависит только от библиотек [`planner-engine`](../planner-engine) и [`layout`](../layout), а не от Spring Boot-приложения `planner`. В classpath стенда нет ни БД, ни веб-сервера, поэтому JMH меряет сам алгоритм. Полный список стратегий стенд получает из контейнера Spring так же, как сервис, без ручной регистрации (§12.3).

| Пакет | Содержимое |
| --- | --- |
| [`generator/`](src/main/java/io/github/r0mbaa/wms/research/generator) | Перебор параметров топологии под план эксперимента |
| [`ordersource/`](src/main/java/io/github/r0mbaa/wms/research/ordersource) | Интерфейс `OrderSource` и его реализации |
| [`simulation/`](src/main/java/io/github/r0mbaa/wms/research/simulation) | Имитационная модель сборки, цикл дискретных событий |
| [`experiments/`](src/main/java/io/github/r0mbaa/wms/research/experiments) | Прогон серий, планы экспериментов, выгрузка CSV |
| [`benchmark/`](src/jmh/java/io/github/r0mbaa/wms/research/benchmark) | JMH-бенчмарки алгоритмов |

**Требования:** FR-M14-01 .. FR-M14-11. **План эксперимента:** §14.2. **Метрики:** §14.1.

Граница с Python предельно дешёвая (§12.2): стенд пишет результаты прогонов в CSV, [`analysis/`](../analysis) их читает. Обмен идёт через файлы, а не через API: нет общих доменных моделей, нет контрактов, нет второго сервиса в развёртывании.
