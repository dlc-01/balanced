package io.github.balanced.controlplane.controller;

import io.github.balanced.common.ConfigProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ConfigVersionHeaderFilter extends OncePerRequestFilter {

    private final ConfigProvider configProvider;

    public ConfigVersionHeaderFilter(ConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Config-Version", String.valueOf(configProvider.current().version()));
        chain.doFilter(request, response);
    }
}