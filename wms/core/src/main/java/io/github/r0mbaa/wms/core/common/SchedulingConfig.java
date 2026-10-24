package io.github.r0mbaa.wms.core.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Фоновые задачи по расписанию: снятие просроченных резервов и подобное. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
}
