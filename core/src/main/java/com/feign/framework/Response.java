package com.feign.framework;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstraction for HTTP responses.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
public interface Response {

    /**
     * Creates a Response instance with the given parameters.
     *
     * @param url the request URL
     * @param statusCode the HTTP status code
     * @param headers the response headers
     * @param body the response body as string
     * @return a new Response instance
     */
    static Response of(String url, int statusCode, Map<String, String> headers, String body) {
        return new Response() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public String getUrl() {
                return url;
            }

            @Override
            public String statusText() {
                return switch (statusCode) {
                    case 200 -> "OK";
                    case 201 -> "Created";
                    case 204 -> "No Content";
                    case 301 -> "Moved Permanently";
                    case 302 -> "Found";
                    case 304 -> "Not Modified";
                    case 400 -> "Bad Request";
                    case 401 -> "Unauthorized";
                    case 403 -> "Forbidden";
                    case 404 -> "Not Found";
                    case 405 -> "Method Not Allowed";
                    case 500 -> "Internal Server Error";
                    case 502 -> "Bad Gateway";
                    case 503 -> "Service Unavailable";
                    default -> statusCode >= 200 && statusCode < 300 ? "OK"
                            : statusCode >= 300 && statusCode < 400 ? "Redirect"
                            : statusCode >= 400 && statusCode < 500 ? "Client Error"
                            : "Server Error";
                };
            }

            @Override
            public Map<String, String> headers() {
                return Collections.unmodifiableMap(new HashMap<>(headers));
            }

            @Override
            public byte[] body() {
                return body != null ? body.getBytes() : new byte[0];
            }

            @Override
            public String getBodyAsString() {
                return body != null ? body : "";
            }
        };
    }

    /**
     * Gets the HTTP status code
     * @return the status code
     */
    int statusCode();

    /**
     * Gets the request URL (optional, may be null)
     * @return the request URL
     */
    String getUrl();

    /**
     * Gets the HTTP status text
     * @return the status text (e.g., "OK", "Not Found")
     */
    String statusText();

    /**
     * Gets the response headers
     * @return immutable map of headers
     */
    Map<String, String> headers();

    /**
     * Gets the response body
     * @return the response body, or empty byte array if no body
     */
    default byte[] body() {
        return new byte[0];
    }

    /**
     * Gets the response body as string
     * @return the response body as UTF-8 string
     */
    default String getBodyAsString() {
        return new String(body());
    }

    /**
     * Checks if the response was successful (status code 2xx)
     * @return true if successful, false otherwise
     */
    default boolean successful() {
        int status = statusCode();
        return status >= 200 && status < 300;
    }

    /**
     * Checks if the response indicates client error (status code 4xx)
     * @return true if client error, false otherwise
     */
    default boolean clientError() {
        int status = statusCode();
        return status >= 400 && status < 500;
    }

    /**
     * Checks if the response indicates server error (status code 5xx)
     * @return true if server error, false otherwise
     */
    default boolean serverError() {
        int status = statusCode();
        return status >= 500 && status < 600;
    }

    /**
     * Checks if the response indicates a redirect (status code 3xx)
     * @return true if redirect, false otherwise
     */
    default boolean isRedirect() {
        int status = statusCode();
        return status >= 300 && status < 400;
    }
}
