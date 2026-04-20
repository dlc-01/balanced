package io.github.balanced.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
@RestController
public class UpstreamMockApplication {

    public static void main(String[] args) {
        SpringApplication.run(UpstreamMockApplication.class, args);
    }

    @GetMapping("/")
    public String index() throws UnknownHostException {
        return "Hello from " + InetAddress.getLocalHost().getHostName() + "\n";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}