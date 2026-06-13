package com.feign.spring;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enable Feign client scanning. Annotate any @Configuration class.
 *
 * <p>Usage:
 * <pre>{@code
 * @SpringBootApplication
 * @EnableFeignClients(basePackages = "com.example.api")
 * public class App { ... }
 * }</pre>
 *
 * <p>All interfaces annotated with @FeignClient in the scanned packages
 * are automatically registered as Spring beans and can be @Autowired.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(FeignClientRegistrar.class)
public @interface EnableFeignClients {

    /** Base packages to scan for @FeignClient interfaces. Default: annotated class's package. */
    String[] basePackages() default {};

    /** Alternative to basePackages: specific classes whose packages to scan. */
    Class<?>[] basePackageClasses() default {};
}
