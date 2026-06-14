package com.feign.processor;

import com.feign.framework.FeignException;
import com.feign.framework.Response;
import com.feign.framework.circuit.CircuitBreaker;
import com.feign.framework.http.Request;
import com.feign.framework.interceptor.FeignInterceptor;
import com.feign.framework.loadbalancer.LeastConnectionsLoadBalancer;
import com.feign.framework.loadbalancer.LoadBalancer;
import com.feign.framework.protocol.ProtocolHandler;
import com.feign.framework.retry.RetryPolicy;

import java.util.List;

/**
 * Sync retry loop with circuit breaker integration.
 */
class RetryExecutor {

    private final ProtocolHandler protocolHandler;
    private final RetryPolicy retryPolicy;
    private final CircuitBreaker circuitBreaker;
    private final LoadBalancer loadBalancer;
    private final UrlResolver urlResolver;
    private final RequestBuilder requestBuilder;
    private final List<FeignInterceptor> interceptors;

    RetryExecutor(ProtocolHandler proto, RetryPolicy retry, CircuitBreaker cb,
                  LoadBalancer lb, UrlResolver urlResolver, RequestBuilder requestBuilder,
                  List<FeignInterceptor> interceptors) {
        this.protocolHandler = proto;
        this.retryPolicy = retry;
        this.circuitBreaker = cb;
        this.loadBalancer = lb;
        this.urlResolver = urlResolver;
        this.requestBuilder = requestBuilder;
        this.interceptors = interceptors;
    }

    Response execute(Request request, String fullUrl) throws FeignException {
        FeignException last = null;
        int max = retryPolicy != null ? retryPolicy.getMaxRetries() : 0;

        for (int attempt = 0; attempt <= max; attempt++) {
            String url = attempt == 0 ? fullUrl : rebuildUrl(request, urlResolver.resolve());
            try {
                Response resp = protocolHandler.execute(rebuildRequest(request, url));
                markLbComplete(url);
                onSuccess();
                return resp;
            } catch (FeignException e) {
                last = e; onFailure(e);
                notifyError(request, e);
                if (!shouldRetry(e, attempt)) throw e;
                sleep();
            } catch (Exception e) {
                last = new FeignException("Request failed: " + url, e);
                onFailure(last);
                notifyError(request, last);
                if (!shouldRetry(e, attempt)) throw last;
                sleep();
            }
        }
        throw last != null ? last : new FeignException("Max retries exceeded");
    }

    private void onSuccess() { if (circuitBreaker != null) circuitBreaker.onSuccess(); }
    private void onFailure(Exception e) { if (circuitBreaker != null) circuitBreaker.onFailure(); }
    private void notifyError(Request req, FeignException e) { interceptors.forEach(i -> i.onError(req, e)); }

    private boolean shouldRetry(Throwable e, int a) {
        return retryPolicy != null && e instanceof Exception && retryPolicy.canRetry((Exception) e, a);
    }

    private void sleep() {
        long ms = retryPolicy != null ? retryPolicy.getRetryInterval() : 1000;
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    private void markLbComplete(String url) {
        if (loadBalancer instanceof LeastConnectionsLoadBalancer lb) lb.markComplete(url);
    }

    private static Request rebuildRequest(Request orig, String newUrl) {
        return Request.of(orig.getMethod(), newUrl, orig.getHeaders(), orig.getBody(), orig.getQueryParams());
    }

    private String rebuildUrl(Request req, String newBase) {
        String orig = req.getUrl();
        int pathStart = orig.indexOf("/", orig.indexOf("://") + 3);
        String path = pathStart > 0 ? orig.substring(pathStart) : "";
        if (newBase.endsWith("/")) newBase = newBase.substring(0, newBase.length() - 1);
        if (!path.startsWith("/")) path = "/" + path;
        return newBase + path;
    }
}
