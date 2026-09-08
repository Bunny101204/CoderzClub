package com.coderzclub.config;

import com.coderzclub.service.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserService userService;

    @InjectMocks
    private JwtFilter jwtFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReplaceAnonymousAuthenticationWithJwtAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/submissions/limits");
        request.addHeader("Authorization", "Bearer test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            // no-op
        };

        Authentication anonymousAuth = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContextHolder.getContext().setAuthentication(anonymousAuth);

        when(jwtUtil.extractUsername("test-token")).thenReturn("alice");
        when(jwtUtil.isTokenValid("test-token", "alice")).thenReturn(true);

        UserDetails userDetails = User.withUsername("alice")
                .password("password")
                .authorities("ROLE_USER")
                .build();
        when(userService.loadUserByUsername("alice")).thenReturn(userDetails);

        jwtFilter.doFilterInternal(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isSameAs(userDetails);
        assertThat(authentication.getAuthorities()).extracting(a -> a.getAuthority()).contains("ROLE_USER");
        verify(userService).loadUserByUsername("alice");
    }

    @Test
    void shouldTrimWhitespaceAroundBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/submissions/limits");
        request.addHeader("Authorization", "   Bearer   test-token   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            // no-op
        };

        when(jwtUtil.extractUsername("test-token")).thenReturn("alice");
        when(jwtUtil.isTokenValid("test-token", "alice")).thenReturn(true);

        UserDetails userDetails = User.withUsername("alice")
                .password("password")
                .authorities("ROLE_USER")
                .build();
        when(userService.loadUserByUsername("alice")).thenReturn(userDetails);

        jwtFilter.doFilterInternal(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isSameAs(userDetails);
    }
}
