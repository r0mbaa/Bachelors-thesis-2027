package io.github.r0mbaa.wms.core.allocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Резервное правило «минимум затрагиваемых ячеек» (FR-M6-02): сначала ячейки с наибольшим
 * свободным остатком, при равенстве — по коду, чтобы результат был детерминирован. Меньше ячеек
 * — меньше точек в маршруте сборщика. В зоне с выделенным местом у SKU одна ячейка, и правило
 * вырождается в тривиальное (INV-08).
 */
@Component
class FewestCellsRule implements AllocationRule {

    @Override
    public String name() {
        return "fewest_cells";
    }

    @Override
    public List<Take> choose(int quantity, List<Candidate> candidates) {
        List<Candidate> ordered = candidates.stream()
                .filter(c -> c.available() > 0)
                .sorted(Comparator.comparingInt(Candidate::available).reversed().thenComparing(Candidate::code))
                .toList();
        List<Take> takes = new ArrayList<>();
        int remaining = quantity;
        for (Candidate candidate : ordered) {
            if (remaining == 0) {
                break;
            }
            int take = Math.min(remaining, candidate.available());
            takes.add(new Take(candidate.locationId(), take));
            remaining -= take;
        }
        return takes;
    }
}
