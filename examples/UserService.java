package examples;

import com.feign.framework.annotations.FeignClient;
import com.feign.framework.annotations.FeignMethod;
import com.feign.framework.http.HttpMethod;

/**
 * Example Feign client interface for user service.
 */
@FeignClient(name = "user-service", url = "http://localhost:8080")
public interface UserService {

    /**
     * Get user by ID.
     *
     * @param id User ID
     * @return User information
     */
    @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
    String getUser(Long id);

    /**
     * Create a new user.
     *
     * @param user User information
     * @return Created user information
     */
    @FeignMethod(method = HttpMethod.POST, path = {"users"})
    String createUser(String user);
}
