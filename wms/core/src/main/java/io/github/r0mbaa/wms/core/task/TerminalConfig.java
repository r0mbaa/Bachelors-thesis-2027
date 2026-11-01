package io.github.r0mbaa.wms.core.task;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TerminalProperties.class)
class TerminalConfig {
}
