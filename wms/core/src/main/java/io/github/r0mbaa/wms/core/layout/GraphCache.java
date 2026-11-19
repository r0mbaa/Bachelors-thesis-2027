package io.github.r0mbaa.wms.core.layout;

import io.github.r0mbaa.wms.layout.graph.GraphBuilder;
import io.github.r0mbaa.wms.layout.graph.WarehouseGraph;
import io.github.r0mbaa.wms.layout.model.Layout;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Графы складов по версиям планировки. Ключ включает версию, а версии неизменяемы, поэтому
 * граф из кэша никогда не устаревает: новая версия просто получает новый ключ (§8.7, Д4).
 */
@Component
class GraphCache {

    private static final int CAPACITY = 16;

    private final Map<String, WarehouseGraph> graphs = new LinkedHashMap<>(CAPACITY, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, WarehouseGraph> eldest) {
            return size() > CAPACITY;
        }
    };

    synchronized WarehouseGraph get(Layout layout) {
        return graphs.computeIfAbsent(layout.warehouseCode() + "@" + layout.version(), key -> GraphBuilder.build(layout));
    }
}
