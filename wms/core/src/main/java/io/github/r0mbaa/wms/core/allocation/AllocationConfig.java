package io.github.r0mbaa.wms.core.allocation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AllocationProperties.class)
class AllocationConfig {
}
