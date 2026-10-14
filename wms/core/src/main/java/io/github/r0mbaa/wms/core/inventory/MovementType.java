package io.github.r0mbaa.wms.core.inventory;

/** Тип движения (FR-M4-04) и допустимое для него направление; то же правило проверяет CHECK в БД. */
public enum MovementType {
    /** Приход: товар появляется на складе. */
    RECEIPT(Direction.IN),
    /** Размещение из зоны приёмки в ячейку. */
    PUTAWAY(Direction.BETWEEN),
    /** Внутреннее перемещение, в том числе пополнение зоны отбора из навала (FR-M4-09). */
    TRANSFER(Direction.BETWEEN),
    /** Отбор: с полки в тару сборщика (FR-M10-11). */
    PICK(Direction.BETWEEN),
    /** Отгрузка: окончательное списание со склада (FR-M11-05). */
    SHIP(Direction.OUT),
    /** Корректировка по факту пересчёта: в одну сторону, приход или расход. */
    ADJUSTMENT(Direction.EITHER),
    WRITE_OFF(Direction.OUT),
    /** Возврат из тары в ячейку, например при отмене собранного заказа (FR-M10-12). */
    RETURN(Direction.BETWEEN);

    private final Direction direction;

    MovementType(Direction direction) {
        this.direction = direction;
    }

    void requireDirection(boolean hasFrom, boolean hasTo) {
        boolean valid = switch (direction) {
            case IN -> !hasFrom && hasTo;
            case OUT -> hasFrom && !hasTo;
            case BETWEEN -> hasFrom && hasTo;
            case EITHER -> hasFrom != hasTo;
        };
        if (!valid) {
            throw new IllegalArgumentException("Движение " + this + " " + direction.rule);
        }
    }

    private enum Direction {
        IN("только приходит в место хранения, источника у него нет"),
        OUT("только уходит из места хранения, приёмника у него нет"),
        BETWEEN("идёт из одного места хранения в другое"),
        EITHER("затрагивает ровно одно место хранения");

        private final String rule;

        Direction(String rule) {
            this.rule = rule;
        }
    }
}
