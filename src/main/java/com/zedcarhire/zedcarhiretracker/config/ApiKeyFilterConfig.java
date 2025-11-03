package com.zedcarhire.zedcarhiretracker.config;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyFilterConfig extends OncePerRequestFilter {

    @Value("${api.key}")
    private String apiKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Allow health endpoint without key
        return path.startsWith("/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String key = req.getHeader("X-API-Key");
        if (key == null || !key.equals(apiKey)) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.getWriter().write("{\"ok\":false,\"error\":\"unauthorized\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}

