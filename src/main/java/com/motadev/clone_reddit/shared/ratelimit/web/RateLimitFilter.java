package com.motadev.clone_reddit.shared.ratelimit.web;

import com.motadev.clone_reddit.shared.exception.RateLimitExceededException;
import com.motadev.clone_reddit.shared.ratelimit.config.RateLimitKeyType;
import com.motadev.clone_reddit.shared.ratelimit.config.RateLimitPolicyProperties;
import com.motadev.clone_reddit.shared.ratelimit.config.RateLimitProperties;
import com.motadev.clone_reddit.shared.ratelimit.config.RateLimitRuleProperties;
import com.motadev.clone_reddit.shared.ratelimit.logging.RateLimitEventLog;
import com.motadev.clone_reddit.shared.ratelimit.store.RateLimitDecision;
import com.motadev.clone_reddit.shared.ratelimit.store.RateLimitStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

public class RateLimitFilter extends OncePerRequestFilter implements Ordered {

    private static final int FILTER_ORDER = 0;

    private final RateLimitProperties properties;
    private final RateLimitStore rateLimitStore;
    private final RateLimitEventLog rateLimitEventLog;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(
            RateLimitProperties properties,
            RateLimitStore rateLimitStore,
            RateLimitEventLog rateLimitEventLog,
            HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.properties = properties;
        this.rateLimitStore = rateLimitStore;
        this.rateLimitEventLog = rateLimitEventLog;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }
        String path = pathWithinApplication(request);
        return path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        RateLimitRuleProperties rule = resolveRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitPolicyProperties policy = properties.policies().get(rule.policy());
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String identity = resolveIdentity(request, rule.key());
        String bucketKey = "ratelimit:" + rule.policy() + ":" + rule.key().name() + ":" + identity;
        String path = pathWithinApplication(request);

        RateLimitDecision decision;
        try {
            decision = rateLimitStore.consume(bucketKey, policy.limit(), policy.windowSeconds());
        } catch (RuntimeException ex) {
            rateLimitEventLog.storeFailed(rule.policy(), path, ex);
            filterChain.doFilter(request, response);
            return;
        }

        applyHeaders(response, decision);

        if (!decision.allowed()) {
            rateLimitEventLog.exceeded(rule.policy(), rule.key(), path);
            handlerExceptionResolver.resolveException(
                    request,
                    response,
                    null,
                    new RateLimitExceededException(
                            rule.policy(),
                            decision.limit(),
                            decision.remaining(),
                            decision.resetEpochSeconds(),
                            decision.retryAfterSeconds()
                    )
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitRuleProperties resolveRule(HttpServletRequest request) {
        String method = request.getMethod();
        String path = pathWithinApplication(request);
        for (RateLimitRuleProperties rule : properties.rules()) {
            if (rule.method().equalsIgnoreCase(method) && pathMatcher.match(rule.pathPattern(), path)) {
                return rule;
            }
        }
        return null;
    }

    private String resolveIdentity(HttpServletRequest request, RateLimitKeyType keyType) {
        if (keyType == RateLimitKeyType.USER) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null
                    && authentication.isAuthenticated()
                    && authentication.getName() != null
                    && !"anonymousUser".equals(authentication.getName())) {
                return authentication.getName();
            }
        }
        return clientIp(request);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private static void applyHeaders(HttpServletResponse response, RateLimitDecision decision) {
        response.setHeader(RateLimitHeaders.LIMIT, String.valueOf(decision.limit()));
        response.setHeader(RateLimitHeaders.REMAINING, String.valueOf(decision.remaining()));
        response.setHeader(RateLimitHeaders.RESET, String.valueOf(decision.resetEpochSeconds()));
        if (!decision.allowed()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        }
    }
}
