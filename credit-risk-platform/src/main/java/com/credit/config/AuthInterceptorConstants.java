package com.credit.config;

/**
 * 登录拦截器白名单路径（无需 UserHolder / authorization）。
 */
public final class AuthInterceptorConstants {

    private AuthInterceptorConstants() {
    }

    public static final String[] LOGIN_EXCLUDE_PATTERNS = {
            "/user/code",
            "/user/login",
            "/upload/**",
            "/internal/**",
            "/error",
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/v2/api-docs",
            "/v3/api-docs/**",
            "/doc.html",
            "/webjars/**"
    };

    public static boolean isPublicPath(String method, String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        String path = uri.endsWith("/") && uri.length() > 1 ? uri.substring(0, uri.length() - 1) : uri;
        if ("POST".equalsIgnoreCase(method)
                && ("/user/code".equals(path) || "/user/login".equals(path))) {
            return true;
        }
        if (path.startsWith("/upload/")
                || path.startsWith("/internal/")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-resources/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars/")) {
            return true;
        }
        return "/error".equals(path)
                || "/swagger-ui.html".equals(path)
                || "/v2/api-docs".equals(path)
                || "/doc.html".equals(path);
    }
}
