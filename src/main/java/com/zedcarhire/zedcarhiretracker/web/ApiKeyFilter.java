package com.zedcarhire.zedcarhiretracker.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Unified API Key Filter
 * Supports multiple API keys from tracking.apiKeys property
 * Checks both X-API-KEY and X-API-Key headers for compatibility
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    @Value("#{'${tracking.apiKeys}'.split(',')}")
    private List<String> validKeys;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Try both header variations (X-API-KEY and X-API-Key)
        String key = request.getHeader("X-API-KEY");
        if (key == null) {
            key = request.getHeader("X-API-Key");
        }

        // DEBUG LOGGING - Remove these after fixing
        System.out.println("========================================");
        System.out.println("=== API KEY FILTER DEBUG ===");
        System.out.println("========================================");
        System.out.println("URI: " + uri);
        System.out.println("Method: " + method);
        System.out.println("Received API Key: [" + (key != null ? key : "NULL") + "]");
        System.out.println("Valid Keys Configured: " + validKeys.size());

        // Print each valid key for comparison (trimmed)
        for (int i = 0; i < validKeys.size(); i++) {
            String trimmedKey = validKeys.get(i).trim();
            System.out.println("  Valid Key [" + i + "]: [" + trimmedKey + "] (length: " + trimmedKey.length() + ")");
        }

        if (key != null) {
            System.out.println("Received Key Length: " + key.length());
        }

        // Check if API key is missing
        if (key == null) {
            System.out.println("REJECTED: No API key provided in request");
            System.out.println("========================================\n");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"ok\":false,\"error\":\"unauthorized\"}");
            return;
        }

        // Validate API key
        final String receivedKey = key.trim();
        boolean isValid = validKeys.stream()
                .anyMatch(k -> k.trim().equals(receivedKey));

        System.out.println("Key Validation Result: " + (isValid ? "VALID ✓" : "INVALID ✗"));

        if (!isValid) {
            System.out.println("REJECTED: API key does not match any configured key");
            System.out.println("========================================\n");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"ok\":false,\"error\":\"unauthorized\"}");
            return;
        }

        System.out.println("ACCEPTED: Valid API key - proceeding to controller");
        System.out.println("========================================\n");
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Skip filter for health endpoint and non-API paths
        boolean skip = path.startsWith("/health") || !path.startsWith("/api/");

        if (skip) {
            System.out.println("[FILTER SKIPPED] Path: " + path);
        }

        return skip;
    }
}