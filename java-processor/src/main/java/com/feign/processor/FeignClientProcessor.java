package com.feign.processor;

import com.google.auto.service.AutoService;
import com.feign.framework.annotations.FeignClient;
import com.feign.framework.annotations.FeignMethod;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

/**
 * Annotation processor for Feign client annotations.
 * Discovers @FeignClient annotated classes and validates their methods.
 */
@SupportedAnnotationTypes({"com.feign.framework.annotations.FeignClient"})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class FeignClientProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);

            for (Element element : annotatedElements) {
                if (element.getKind() == ElementKind.INTERFACE) {
                    processFeignClientInterface((TypeElement) element);
                } else {
                    processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@FeignClient can only be applied to interfaces",
                        element
                    );
                }
            }
        }

        return true;
    }

    private void processFeignClientInterface(TypeElement interfaceElement) {
        FeignClient clientAnnotation = interfaceElement.getAnnotation(FeignClient.class);
        if (clientAnnotation == null) {
            return;
        }

        processingEnv.getMessager().printMessage(
            Diagnostic.Kind.NOTE,
            "Processing Feign client interface: " + interfaceElement.getSimpleName()
        );

        // Collect all methods annotated with @FeignMethod
        Set<ExecutableElement> feignMethods = ElementFilter.methodsIn(interfaceElement.getEnclosedElements())
            .stream()
            .filter(m -> m.getAnnotation(FeignMethod.class) != null)
            .collect(Collectors.toSet());

        if (feignMethods.isEmpty()) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.WARNING,
                "No methods with @FeignMethod annotation found in " + interfaceElement.getSimpleName()
            );
        } else {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.NOTE,
                "Found " + feignMethods.size() + " methods with @FeignMethod annotation"
            );
        }

        // Create metadata from annotations
        String serviceName = clientAnnotation.name();
        String url = clientAnnotation.url();
        String loadBalancer = clientAnnotation.loadBalancer().name();
        String[] path = clientAnnotation.path();

        FeignClientMetadata metadata = new FeignClientMetadata(
            serviceName,
            url,
            loadBalancer,
            path
        );

        processingEnv.getMessager().printMessage(
            Diagnostic.Kind.NOTE,
            "Created metadata: " + metadata.getServiceName() + " - " + metadata.getUrl()
        );
    }
}
