package examples;

import com.feign.framework.FeignException;
import com.feign.processor.FeignClientFactory;

/**
 * Demo application showing how to use Feign client proxies.
 */
public class FeignClientDemo {

    public static void main(String[] args) {
        try {
            System.out.println("Creating UserService proxy...");

            // Create proxy instance using FeignClientFactory
            UserService userService = FeignClientFactory.create(UserService.class);

            System.out.println("UserService proxy created successfully!");
            System.out.println("Proxy type: " + userService.getClass().getName());

            // Note: Actual HTTP calls would require a running server
            // This demonstrates proxy creation and method invocation

        } catch (FeignException e) {
            System.err.println("Failed to create Feign client: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
