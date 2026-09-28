package io.github.r0mbaa.wms.layout.model;

/** Точка на плане склада, м. Ось X направлена вправо, ось Y вверх (на север). */
public record Point(double x, double y) {

    public Point {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Координаты точки должны быть конечными числами: (" + x + ", " + y + ")");
        }
    }
}
