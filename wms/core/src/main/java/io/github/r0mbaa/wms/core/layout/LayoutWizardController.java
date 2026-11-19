package io.github.r0mbaa.wms.core.layout;

import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import io.github.r0mbaa.wms.layout.cells.CellGenerator;
import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.RackProfile;
import io.github.r0mbaa.wms.layout.model.StandardProfiles;
import io.github.r0mbaa.wms.layout.wizard.DepotPosition;
import io.github.r0mbaa.wms.layout.wizard.RectangularLayoutParameters;
import io.github.r0mbaa.wms.layout.wizard.RectangularLayoutWizard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Мастер типовой прямоугольной планировки (FR-M15-09) и типовые профили стеллажей
 * (FR-M15-03d). Мастер ничего не сохраняет: он возвращает документ планировки, который
 * конструктор показывает и сохраняет обычным {@code PUT …/layout}.
 */
@RestController
class LayoutWizardController {

    private final TopologyService topology;

    LayoutWizardController(TopologyService topology) {
        this.topology = topology;
    }

    @GetMapping("/api/v1/rack-profiles/standard")
    List<RackProfile> standardProfiles() {
        return StandardProfiles.ALL;
    }

    /** Документ строится поверх текущей версии, поэтому его можно сразу сохранить. */
    @PostMapping("/api/v1/warehouses/{code}/layout/wizard")
    WizardResult generate(@PathVariable String code, @Valid @RequestBody WizardRequest request) {
        Warehouse warehouse = topology.warehouse(code);
        RectangularLayoutParameters parameters = new RectangularLayoutParameters(warehouse.getCode(),
                request.aisles(), request.crossAisles(), request.sectionsPerBlock(), request.resolveProfile(),
                request.aisleWidth(), request.crossAisleWidth(), request.depotPosition());
        Layout layout = RectangularLayoutWizard.generate(parameters, warehouse.getLayoutVersion());
        return new WizardResult(layout, parameters.blockLength(), parameters.aislePitch(), parameters.shapeFactor(),
                CellGenerator.generate(layout).size());
    }

    /**
     * @param standardProfile имя типового профиля; или {@code profile} — свой профиль
     */
    record WizardRequest(int aisles, int crossAisles, int sectionsPerBlock, String standardProfile,
            RackProfile profile, double aisleWidth, double crossAisleWidth, @NotNull DepotPosition depotPosition) {

        RackProfile resolveProfile() {
            if (profile != null) {
                return profile;
            }
            if (standardProfile == null || standardProfile.isBlank()) {
                throw new IllegalArgumentException("Укажите профиль стеллажей: имя типового или свой профиль");
            }
            return StandardProfiles.byName(standardProfile);
        }
    }

    /**
     * @param blockLength длина прохода в блоке {@code L}, м
     * @param aislePitch  шаг проходов {@code d}, м
     * @param shapeFactor коэффициент формы {@code k = L / d} (§8.1)
     */
    record WizardResult(Layout layout, double blockLength, double aislePitch, double shapeFactor, int cells) {
    }
}
