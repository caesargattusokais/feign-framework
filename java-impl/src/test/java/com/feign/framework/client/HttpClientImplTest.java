package com.feign.framework.client;

import com.feign.framework.Response;
import com.feign.framework.http.Request;
import com.feign.framework.http.HttpMethod;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class HttpClientImplTest {

    @Test
    void testExecuteGETRequest() throws Exception {
        HttpClientImpl client = new HttpClientImpl(new HttpClientConfig());
        Request request = Request.of(
            HttpMethod.GET,
            "https://jsonplaceholder.typicode.com/users/1",
            new HashMap<>(),
            null,
            new HashMap<>()
        );

        Response response = client.execute(request);

        assertNotNull(response);
        assertEquals(200, response.statusCode());
        assertNotNull(response.body());
    }

    @Test
    void testExecutePOSTRequest() throws Exception {
        HttpClientImpl client = new HttpClientImpl(new HttpClientConfig());
        Request request = Request.of(
            HttpMethod.POST,
            "https://jsonplaceholder.typicode.com/posts",
            new HashMap<>(),
            "{\"title\":\"test\",\"body\":\"test body\",\"userId\":1}",
            new HashMap<>()
        );

        Response response = client.execute(request);

        assertNotNull(response);
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300);
    }

    @Test
    void testExecuteAsyncGETRequest() throws Exception {
        HttpClientImpl client = new HttpClientImpl(new HttpClientConfig());
        Request request = Request.of(
            HttpMethod.GET,
            "https://jsonplaceholder.typicode.com/users/1",
            new HashMap<>(),
            null,
            new HashMap<>()
        );

        java.util.concurrent.CompletableFuture<Response> future = client.executeAsync(request);

        Response response = future.join();

        assertNotNull(response);
        assertEquals(200, response.statusCode());
    }

    @Test
    void testExecuteAsyncPOSTRequest() throws Exception {
        HttpClientImpl client = new HttpClientImpl(new HttpClientConfig());
        Request request = Request.of(
            HttpMethod.POST,
            "https://jsonplaceholder.typicode.com/posts",
            new HashMap<>(),
            "{\"title\":\"test\",\"body\":\"test body\",\"userId\":1}",
            new HashMap<>()
        );

        java.util.concurrent.CompletableFuture<Response> future = client.executeAsync(request);

        Response response = future.join();

        assertNotNull(response);
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300);
    }

    @Test
    void testIsAvailable() {
        HttpClientImpl client = new HttpClientImpl(new HttpClientConfig());
        assertTrue(client.isAvailable());
    }

    @Test
    void testGetName() {
        HttpClientImpl client = new HttpClientImpl(new HttpClientConfig());
        assertEquals("Apache-HttpClient-5", client.getName());
    }

    @Test
    void testExecuteError404() throws Exception {
        HttpClientImpl client = new HttpClientImpl(new HttpClientConfig());
        Request request = Request.of(
            HttpMethod.GET,
            "https://jsonplaceholder.typicode.com/posts/999999999",
            new HashMap<>(),
            null,
            new HashMap<>()
        );

        Response response = client.execute(request);

        assertNotNull(response);
        assertEquals(404, response.statusCode());
        assertTrue(response.clientError());
    }
}
