package com.sindhueventpay.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that assigns a unique correlation ID to every incoming request.
 *
 * <p>The ID is:
 * <ul>
 *   <li>Read from the incoming {@code X-Correlation-Id} header if present
 *       (useful for tracing from an upstream gateway or client).</li>
 *   <li>Generated fresh as a UUID v4 otherwise.</li>
 * </ul>
 *
 * <p>The correlation ID is:
 * <ul>
 *   <li>Stored in {@link MDC} so it appears in every log line for this request
 *       (via {@code %X{correlationId}} in the log pattern).</li>
 *   <li>Echoed back in the {@code X-Correlation-Id} response header so clients
 *       can include it in support tickets.</li>
 * </ul>
 *
 * <p>MDC is always cleared after the request to prevent thread-pool leakage.
 */
@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY); // prevent leakage in thread pools
        }
    }
}
