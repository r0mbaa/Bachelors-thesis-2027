package io.github.r0mbaa.wms.core.topology;

import io.github.r0mbaa.wms.core.catalog.StorageClass;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Набор классов хранения в одной колонке через запятую. Классов четыре, и отдельная таблица
 * ради них умножила бы запросы при выдаче списка ячеек.
 */
@Converter
class StorageClassesConverter implements AttributeConverter<Set<StorageClass>, String> {

    @Override
    public String convertToDatabaseColumn(Set<StorageClass> classes) {
        if (classes == null || classes.isEmpty()) {
            return null;
        }
        return classes.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<StorageClass> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(Arrays.stream(column.split(","))
                .map(StorageClass::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(StorageClass.class))));
    }
}
