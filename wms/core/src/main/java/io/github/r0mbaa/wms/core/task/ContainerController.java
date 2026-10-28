package io.github.r0mbaa.wms.core.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
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

@RestController
@RequestMapping("/api/v1/containers")
class ContainerController {

    private final ContainerService containers;

    ContainerController(ContainerService containers) {
        this.containers = containers;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    ContainerView create(@Valid @RequestBody CreateContainerRequest request) {
        return ContainerView.from(containers.create(request.warehouse(), request.code(), request.type(),
                request.maxVolumeM3(), request.maxWeightKg(), request.slots()));
    }

    @GetMapping
    List<ContainerView> list(@RequestParam String warehouse) {
        return containers.list(warehouse).stream().map(ContainerView::from).toList();
    }

    @GetMapping("/{code}")
    ContainerView get(@PathVariable String code) {
        return ContainerView.from(containers.get(code));
    }

    @PutMapping("/{code}/active")
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    ContainerView setActive(@PathVariable String code, @Valid @RequestBody ActiveRequest request) {
        return ContainerView.from(containers.setActive(code, request.active()));
    }

    record CreateContainerRequest(@NotBlank String warehouse, @NotBlank String code, @NotNull ContainerType type,
            @Positive double maxVolumeM3, @Positive double maxWeightKg, @Positive int slots) {
    }

    record ActiveRequest(@NotNull Boolean active) {
    }

    /** @param location виртуальное место, где лежит отобранное в эту тару */
    record ContainerView(String code, ContainerType type, double maxVolumeM3, double maxWeightKg, int slots,
            boolean active, String location, String qrPayload) {

        static ContainerView from(Container c) {
            return new ContainerView(c.getCode(), c.getType(), c.getMaxVolumeM3(), c.getMaxWeightKg(), c.getSlots(),
                    c.isActive(), c.getLocation().getCode(), c.qrPayload());
        }
    }
}
