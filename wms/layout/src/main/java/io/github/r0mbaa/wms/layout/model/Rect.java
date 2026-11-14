package io.github.r0mbaa.wms.layout.model;

/** Прямоугольник на плане со сторонами вдоль осей, м. */
public record Rect(double minX, double minY, double maxX, double maxY) {

    public Rect {
        if (!(minX <= maxX) || !(minY <= maxY)) {
            throw new IllegalArgumentException("Прямоугольник вывернут: [" + minX + ", " + maxX + "] × ["
                    + minY + ", " + maxY + "]");
        }
    }

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }

    /** Пересечение с положительной площадью: касание сторонами пересечением не считается. */
    public boolean overlaps(Rect other, double eps) {
        return minX < other.maxX - eps && other.minX < maxX - eps
                && minY < other.maxY - eps && other.minY < maxY - eps;
    }

    public boolean contains(Rect other, double eps) {
        return other.minX >= minX - eps && other.maxX <= maxX + eps
                && other.minY >= minY - eps && other.maxY <= maxY + eps;
    }

    public boolean contains(Point p, double eps) {
        return p.x() >= minX - eps && p.x() <= maxX + eps && p.y() >= minY - eps && p.y() <= maxY + eps;
    }

    /** Точка строго внутри: на границе не считается. */
    public boolean containsStrictly(Point p) {
        return p.x() > minX && p.x() < maxX && p.y() > minY && p.y() < maxY;
    }

    public Rect expand(double by) {
        return new Rect(minX - by, minY - by, maxX + by, maxY + by);
    }

    /** Общая часть прямоугольников, которые {@link #overlaps пересекаются}. */
    public Rect intersection(Rect other) {
        return new Rect(Math.max(minX, other.minX), Math.max(minY, other.minY),
                Math.min(maxX, other.maxX), Math.min(maxY, other.maxY));
    }
}
