package com.feign.framework;

import java.io.IOException;
import java.io.Serial;

/**
 * Exception thrown by Feign framework when errors occur during request execution.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
public class FeignException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int status;
    private final String method;
    private final String url;

    /**
     * Creates a new FeignException with a message.
     *
     * @param message the exception message
     */
    public FeignException(String message) {
        super(message);
        this.status = -1;
        this.method = null;
        this.url = null;
    }

    /**
     * Creates a new FeignException with a message and cause.
     *
     * @param message the exception message
     * @param cause the cause of this exception
     */
    public FeignException(String message, Throwable cause) {
        super(message, cause);
        this.status = -1;
        this.method = null;
        this.url = null;
    }

    /**
     * Creates a new FeignException with status, message, method, and URL.
     *
     * @param status the HTTP status code
     * @param message the exception message
     * @param method the HTTP method
     * @param url the request URL
     */
    public FeignException(int status, String message, String method, String url) {
        super(message);
        this.status = status;
        this.method = method;
        this.url = url;
    }

    /**
     * Creates a new FeignException with status, message, method, URL, and cause.
     *
     * @param status the HTTP status code
     * @param message the exception message
     * @param method the HTTP method
     * @param url the request URL
     * @param cause the cause of this exception
     */
    public FeignException(int status, String message, String method, String url, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.method = method;
        this.url = url;
    }

    /**
     * Creates a new FeignException for HTTP client errors (4xx).
     *
     * @param status the HTTP status code
     * @param method the HTTP method
     * @param url the request URL
     */
    public FeignException(int status, String method, String url) {
        this(status, getStatusText(status), method, url);
    }

    /**
     * Creates a new FeignException for HTTP server errors (5xx).
     *
     * @param status the HTTP status code
     * @param method the HTTP method
     * @param url the request URL
     * @param cause the cause of this exception
     */
    public FeignException(int status, String method, String url, Throwable cause) {
        this(status, getStatusText(status), method, url, cause);
    }

    /**
     * Creates a new FeignException from a cause.
     *
     * @param cause the cause of this exception
     */
    public FeignException(Throwable cause) {
        super(cause);
        this.status = -1;
        this.method = null;
        this.url = null;
    }

    /**
     * Creates a new FeignException from an IOException.
     *
     * @param cause the cause of this exception
     */
    public FeignException(IOException cause) {
        super(cause.getMessage(), cause);
        this.status = -1;
        this.method = null;
        this.url = null;
    }

    /**
     * Gets the HTTP status code.
     *
     * @return the HTTP status code, or -1 if not available
     */
    public int getStatus() {
        return status;
    }

    /**
     * Gets the HTTP method.
     *
     * @return the HTTP method, or null if not available
     */
    public String getMethod() {
        return method;
    }

    /**
     * Gets the request URL.
     *
     * @return the request URL, or null if not available
     */
    public String getUrl() {
        return url;
    }

    /**
     * Gets the HTTP status text for the given status code.
     *
     * @param status the HTTP status code
     * @return the status text
     */
    private static String getStatusText(int status) {
        return switch (status) {
            case 100 -> "Continue";
            case 101 -> "Switching Protocols";
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 203 -> "Non-Authoritative Information";
            case 204 -> "No Content";
            case 205 -> "Reset Content";
            case 206 -> "Partial Content";
            case 300 -> "Multiple Choices";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 303 -> "See Other";
            case 304 -> "Not Modified";
            case 305 -> "Use Proxy";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 402 -> "Payment Required";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 406 -> "Not Acceptable";
            case 407 -> "Proxy Authentication Required";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 410 -> "Gone";
            case 411 -> "Length Required";
            case 412 -> "Precondition Failed";
            case 413 -> "Payload Too Large";
            case 414 -> "URI Too Long";
            case 415 -> "Unsupported Media Type";
            case 416 -> "Range Not Satisfiable";
            case 417 -> "Expectation Failed";
            case 418 -> "I'm a teapot";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 501 -> "Not Implemented";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            case 505 -> "HTTP Version Not Supported";
            default -> "Unknown Status";
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FeignException: ").append(getMessage());

        if (method != null) {
            sb.append(" [").append(method).append(" ");
        }

        if (url != null) {
            sb.append(url);
        }

        if (method != null) {
            sb.append("]");
        }

        if (status != -1) {
            sb.append(" (Status: ").append(status).append(" ").append(getStatusText(status)).append(")");
        }

        return sb.toString();
    }

    @Override
    public String getMessage() {
        String msg = super.getMessage();
        if (method != null && url != null) {
            if (msg != null) {
                return msg + " [" + method + " " + url + "]";
            } else {
                return "[" + method + " " + url + "]";
            }
        }
        return msg;
    }
}
