package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.admin.Role;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param manualEntryRoles роли, которым разрешён ручной ввод кода вместо сканирования
 *                         (FR-M10-04b, NFR-SEC-06). Сборщику обычно не разрешён: при
 *                         повреждённой этикетке код вводит диспетчер, и это видно в аудите
 */
@ConfigurationProperties("wms.terminal")
record TerminalProperties(@DefaultValue({"DISPATCHER", "WAREHOUSE_ADMIN"}) Set<Role> manualEntryRoles) {
}
