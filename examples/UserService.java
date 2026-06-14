package examples;

import com.feign.framework.annotations.FeignClient;
import com.feign.framework.annotations.FeignMethod;
import com.feign.framework.annotations.Path;
import com.feign.framework.annotations.Query;
import com.feign.framework.http.HttpMethod;
import com.feign.framework.FeignResponse;

import java.util.List;
import java.util.Map;

/**
 * Basic Feign client interface for user service.
 * Demonstrates @Path, @Query, @FeignResponse.
 */
@FeignClient(name = "user-service", url = "http://localhost:8080/api")
public interface UserService {

    @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
    Map<String, Object> getUser(@Path("id") Long id);

    @FeignMethod(method = HttpMethod.POST, path = {"users"})
    Map<String, Object> createUser(Map<String, Object> user);

    @FeignMethod(method = HttpMethod.GET, path = {"users"})
    List<Map<String, Object>> listUsers(@Query("page") int page, @Query("size") int size);

    /** Returns body + response headers */
    @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
    FeignResponse<Map<String, Object>> getUserWithHeaders(@Path("id") Long id);
}
