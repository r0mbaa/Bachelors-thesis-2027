package io.github.r0mbaa.wms.core.allocation;

import java.util.List;

/**
 * Правило выбора ячеек-источников под строку заказа (FR-M6-02). Состояние резервов остаётся в
 * {@code core}, а сами стратегии аллокации — ближайшая к депо, минимальный остаток, совместная
 * с маршрутизацией (FR-M6-06) — будут в {@code planner-engine}. В {@code core} живёт только
 * резервное правило, которое работает без планировщика (NFR-R-05).
 */
public interface AllocationRule {

    String name();

    /**
     * @param quantity   сколько нужно
     * @param candidates ячейки со свободным остатком этого SKU
     * @return сколько взять из каких ячеек; в сумме не больше {@code quantity} и не больше
     *         свободного в каждой ячейке. Меньше {@code quantity} — строка обеспечена частично
     */
    List<Take> choose(int quantity, List<Candidate> candidates);

    record Candidate(long locationId, String code, int available) {
    }

    record Take(long locationId, int quantity) {
    }
}
