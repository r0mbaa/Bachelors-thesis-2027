package io.github.r0mbaa.wms.core.topology;

import io.github.r0mbaa.wms.core.catalog.StorageClass;
import io.github.r0mbaa.wms.core.common.PageResponse;
import io.github.r0mbaa.wms.core.topology.TopologyViews.LocationView;
import io.github.r0mbaa.wms.core.topology.TopologyViews.WarehouseView;
import io.github.r0mbaa.wms.core.topology.TopologyViews.ZoneView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Чтение топологии доступно всем вошедшим; изменение — администратору склада. */
@RestController
@RequestMapping("/api/v1")
class TopologyController {

    private final TopologyService topology;

    TopologyController(TopologyService topology) {
        this.topology = topology;
    }

    @GetMapping("/warehouses")
    List<WarehouseView> warehouses() {
        return topology.warehouses().stream().map(WarehouseView::from).toList();
    }

    @GetMapping("/warehouses/{code}")
    WarehouseView warehouse(@PathVariable String code) {
        return WarehouseView.from(topology.warehouse(code));
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    WarehouseView createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
        return WarehouseView.from(topology.createWarehouse(request.code(), request.name(), request.defaultSpeedMps()));
    }

    @GetMapping("/warehouses/{code}/zones")
    List<ZoneView> zones(@PathVariable String code) {
        return topology.zones(code).stream().map(ZoneView::from).toList();
    }

    @PostMapping("/warehouses/{code}/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    ZoneView createZone(@PathVariable String code, @Valid @RequestBody CreateZoneRequest request) {
        return ZoneView.from(topology.createZone(code, request.code(), request.name(), request.type(),
                request.storagePolicy()));
    }

    @PutMapping("/warehouses/{code}/zones/{zone}/rows")
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    Map<String, Integer> assignRows(@PathVariable String code, @PathVariable String zone,
            @Valid @RequestBody AssignRowsRequest request) {
        return Map.of("cells", topology.assignRows(code, zone, request.rows()));
    }

    @GetMapping("/warehouses/{code}/locations")
    PageResponse<LocationView> locations(
            @PathVariable String code,
            @RequestParam(required = false) Integer row,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) LocationType type,
            @RequestParam(required = false) Boolean blocked,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return PageResponse.of(topology.search(code, row, zone, type, blocked, page, PageResponse.clampSize(size)),
                LocationView::from);
    }

    @GetMapping("/locations/{code}")
    LocationView location(@PathVariable String code) {
        return LocationView.from(topology.location(code));
    }

    @PostMapping("/locations/{code}/block")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'DISPATCHER')")
    LocationView block(@PathVariable String code, @Valid @RequestBody BlockRequest request) {
        return LocationView.from(topology.block(code, request.reason()));
    }

    @PostMapping("/locations/{code}/unblock")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'DISPATCHER')")
    LocationView unblock(@PathVariable String code) {
        return LocationView.from(topology.unblock(code));
    }

    @PutMapping("/locations/{code}/type")
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    LocationView setType(@PathVariable String code, @Valid @RequestBody TypeRequest request) {
        return LocationView.from(topology.setType(code, request.type()));
    }

    @PutMapping("/locations/{code}/storage-classes")
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    LocationView setStorageClasses(@PathVariable String code, @Valid @RequestBody StorageClassesRequest request) {
        return LocationView.from(topology.setAllowedStorageClasses(code, request.classes()));
    }

    record CreateWarehouseRequest(@NotBlank String code, @NotBlank String name, @Positive double defaultSpeedMps) {
    }

    record CreateZoneRequest(@NotBlank String code, @NotBlank String name, @NotNull LocationType type,
            @NotNull StoragePolicy storagePolicy) {
    }

    record AssignRowsRequest(@NotEmpty Set<Integer> rows) {
    }

    record BlockRequest(@NotBlank String reason) {
    }

    record TypeRequest(@NotNull LocationType type) {
    }

    /** Пустой список снимает ограничение. */
    record StorageClassesRequest(@NotNull Set<StorageClass> classes) {
    }
}
