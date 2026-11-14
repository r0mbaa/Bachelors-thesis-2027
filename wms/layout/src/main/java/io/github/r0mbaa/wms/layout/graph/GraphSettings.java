package io.github.r0mbaa.wms.layout.graph;

/**
 * Параметры построения графа (§8.7). Их значения — допущения работы, фиксируются в тексте.
 *
 * @param mergeWidth    {@code w_merge}: проход не шире этого — одна линия точек отбора посередине
 *                      на обе стены; шире — две линии у каждой стены (А1). По умолчанию 2,5 м,
 *                      порядка двойного вылета руки
 * @param minAisleWidth {@code w_min}: уже этого тележка не проходит (Б2). По умолчанию 1,2 м
 */
public record GraphSettings(double mergeWidth, double minAisleWidth) {

    public static final GraphSettings DEFAULT = new GraphSettings(2.5, 1.2);

    public GraphSettings {
        if (!(minAisleWidth > 0) || !(mergeWidth >= minAisleWidth)) {
            throw new IllegalArgumentException("Нужно 0 < w_min ≤ w_merge, задано w_min = " + minAisleWidth
                    + ", w_merge = " + mergeWidth);
        }
    }

    /**
     * Насколько раздуваются стеллажи при проверке проходимости: ось движения должна отстоять от
     * стеллажа не меньше чем на половину ширины тележки. Минус миллиметр — чтобы проход ровно
     * {@code w_min} оставался проходимым несмотря на арифметику с плавающей точкой.
     */
    double clearance() {
        return minAisleWidth / 2 - 1e-3;
    }
}
