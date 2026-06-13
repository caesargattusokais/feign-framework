package com.feign.framework;

import com.feign.framework.Response;
import com.feign.framework.http.Request;

public interface FeignClient {
    Response execute(Request request);
}
