package io.github.r0mbaa.wms.core.catalog;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SkuRepository extends JpaRepository<Sku, Long> {

    @EntityGraph(attributePaths = "barcodes")
    Optional<Sku> findByArticle(String article);

    boolean existsByArticle(String article);

    @EntityGraph(attributePaths = "barcodes")
    @Query("select b.sku from SkuBarcode b where b.barcode = :barcode")
    Optional<Sku> findByBarcode(String barcode);

    /**
     * @param pattern подстрока артикула или наименования в нижнем регистре, обёрнутая в {@code %};
     *                {@code null} — без фильтра
     */
    @EntityGraph(attributePaths = "barcodes")
    @Query("""
            select s from Sku s
            where :pattern is null or lower(s.article) like :pattern or lower(s.name) like :pattern
            order by s.article
            """)
    Page<Sku> search(String pattern, Pageable pageable);
}
