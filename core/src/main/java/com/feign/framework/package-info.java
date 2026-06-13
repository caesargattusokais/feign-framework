/**
 * Core abstraction layer for Feign framework.
 *
 * <p>This package provides the foundational interfaces and abstractions that define the Feign framework's core behavior,
 * including client definitions, HTTP request/response handling, and interceptor chains.
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@code FeignClient} - Interface for creating Feign HTTP clients</li>
 *   <li>{@code Request} - Abstraction for HTTP requests</li>
 *   <li>{@code Response} - Abstraction for HTTP responses</li>
 *   <li>{@code Interceptor} - Interceptors for request/response processing</li>
 * </ul>
 *
 * <p>The core layer contains no implementation details and is designed to be extended by concrete
 * implementations while maintaining a consistent abstraction contract.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
package com.feign.framework;
