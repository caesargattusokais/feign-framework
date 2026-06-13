package com.feign.spring;

import com.feign.framework.annotations.FeignClient;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Scans classpath for @FeignClient interfaces and registers them as FactoryBeans.
 */
public class FeignClientRegistrar implements ImportBeanDefinitionRegistrar,
        ResourceLoaderAware, EnvironmentAware {

    private ResourceLoader resourceLoader;
    private Environment environment;

    @Override
    public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(@NonNull AnnotationMetadata importingClassMetadata,
                                        @NonNull BeanDefinitionRegistry registry) {

        // Collect base packages from @EnableFeignClients
        Set<String> basePackages = getBasePackages(importingClassMetadata);

        // Scanner that picks up @FeignClient interfaces
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false, environment) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDef) {
                return beanDef.getMetadata().isInterface()
                    && beanDef.getMetadata().hasAnnotation(FeignClient.class.getName());
            }
        };
        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(FeignClient.class));

        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);

            for (BeanDefinition candidate : candidates) {
                String className = candidate.getBeanClassName();
                Class<?> clazz;
                try {
                    clazz = ClassUtils.forName(className, resourceLoader.getClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Failed to load @FeignClient class: " + className, e);
                }

                FeignClient ann = clazz.getAnnotation(FeignClient.class);
                if (ann == null) continue;

                // Register a FactoryBean for this interface
                BeanDefinitionBuilder builder = BeanDefinitionBuilder
                        .genericBeanDefinition(FeignClientFactoryBean.class);
                builder.addConstructorArgValue(clazz);
                builder.addConstructorArgValue(ann.name());
                builder.addConstructorArgValue(ann.url());
                builder.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);

                String beanName = StringUtils.hasText(ann.name())
                        ? ann.name() : ClassUtils.getShortName(className);
                // lowercase first char for bean name
                beanName = Character.toLowerCase(beanName.charAt(0)) + beanName.substring(1);

                AbstractBeanDefinition beanDef = builder.getBeanDefinition();
                beanDef.setPrimary(true);

                BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDef, className, new String[]{beanName});
                BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
            }
        }
    }

    private Set<String> getBasePackages(AnnotationMetadata meta) {
        Set<String> packages = new HashSet<>();
        Map<String, Object> attrs = meta.getAnnotationAttributes(EnableFeignClients.class.getName());
        if (attrs == null) return packages;

        String[] basePackages = (String[]) attrs.get("basePackages");
        if (basePackages != null) {
            for (String pkg : basePackages) {
                if (StringUtils.hasText(pkg)) packages.add(pkg.trim());
            }
        }

        Class<?>[] basePackageClasses = (Class<?>[]) attrs.get("basePackageClasses");
        if (basePackageClasses != null) {
            for (Class<?> clazz : basePackageClasses) {
                packages.add(ClassUtils.getPackageName(clazz));
            }
        }

        if (packages.isEmpty()) {
            packages.add(ClassUtils.getPackageName(meta.getClassName()));
        }

        return packages;
    }
}
