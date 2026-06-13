package com.feign.spring;

import com.feign.framework.codec.GsonDecoder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for Feign Framework.
 *
 * <p>Automatically enabled when {@code feign.client.config.*} properties are present
 * or when {@code @EnableFeignClients} is used.
 *
 * <p>Beans registered:
 * <ul>
 *   <li>{@link FeignProperties} — configuration properties</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(FeignProperties.class)
@ConditionalOnProperty(prefix = "feign.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeignAutoConfiguration {

    /**
     * Default Gson decoder, available as a Spring bean for injection.
     */
    @Bean
    @ConditionalOnMissingBean
    public GsonDecoder feignGsonDecoder() {
        return new GsonDecoder();
    }
}
