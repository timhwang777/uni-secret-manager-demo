package io.github.timhwang777.unisecretdemo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TrailingSlashRedirectFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.length() > 1 && path.endsWith("/")) {
            String normalizedPath = path.substring(0, path.length() - 1);
            HttpServletRequest wrappedRequest = new NormalizedPathRequest(request, normalizedPath);
            filterChain.doFilter(wrappedRequest, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static final class NormalizedPathRequest extends HttpServletRequestWrapper {
        private final String requestUri;
        private final String servletPath;
        private final String pathInfo;

        private NormalizedPathRequest(HttpServletRequest request, String normalizedRequestUri) {
            super(request);
            this.requestUri = normalizedRequestUri;
            this.servletPath = trimTrailingSlash(request.getServletPath());
            this.pathInfo = trimTrailingSlash(request.getPathInfo());
        }

        @Override
        public String getRequestURI() {
            return requestUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer();
            url.append(getScheme())
                    .append("://")
                    .append(getServerName());

            int port = getServerPort();
            if (port > 0 && !isDefaultPort(getScheme(), port)) {
                url.append(':').append(port);
            }

            url.append(requestUri);
            return url;
        }

        @Override
        public String getServletPath() {
            return servletPath;
        }

        @Override
        public String getPathInfo() {
            return pathInfo;
        }

        private static String trimTrailingSlash(String value) {
            if (value == null || value.length() <= 1 || !value.endsWith("/")) {
                return value;
            }
            return value.substring(0, value.length() - 1);
        }

        private static boolean isDefaultPort(String scheme, int port) {
            return ("http".equalsIgnoreCase(scheme) && port == 80)
                    || ("https".equalsIgnoreCase(scheme) && port == 443);
        }
    }
}
