package io.github.timhwang777.unisecretdemo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("local")
class TrailingSlashRedirectFilterTest {

    private final TrailingSlashRedirectFilter filter = new TrailingSlashRedirectFilter();

    @Test
    void shouldNormalizeTrailingSlashWithoutRedirect() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/demo/all/");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);

        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked).isTrue();
        assertThat(chain.capturedRequest.getRequestURI()).isEqualTo("/api/demo/all");
        assertThat(chain.capturedRequest.getRequestURL().toString()).isEqualTo("http://localhost:8080/api/demo/all");
        assertThat(response.getHeader("Location")).isNull();
    }

    @Test
    void shouldPassThroughRequestsWithoutTrailingSlash() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/secrets/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked).isTrue();
        assertThat(chain.capturedRequest).isSameAs(request);
    }

    private static final class CapturingFilterChain implements FilterChain {
        private boolean invoked;
        private HttpServletRequest capturedRequest;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            this.invoked = true;
            this.capturedRequest = (HttpServletRequest) request;
        }
    }
}
