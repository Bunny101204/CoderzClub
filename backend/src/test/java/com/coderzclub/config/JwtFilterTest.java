package com.coderzclub.config;

import com.coderzclub.model.User;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
        lenient().when(jwtUtil.legacyTokenCouldBelongToUser(any(), any()))
            .thenAnswer(invocation -> new JwtUtil().legacyTokenCouldBelongToUser(
                invocation.getArgument(0), invocation.getArgument(1)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uidJwtLoadsUserByIdAndStoresRequestUser() throws Exception {
        MockHttpServletRequest request = requestWithBearer("/api/users/profile", "test-token");
        User dbUser = dbUser("u1", "alice", "USER", true, new Date());
        UserDetails userDetails = details("alice", "ROLE_USER", true);
        when(jwtUtil.extractIdentity("test-token")).thenReturn(identity("alice", "u1", "USER", null));
        when(userService.loadUserForAuthentication("u1", "alice")).thenReturn(dbUser);
        when(userService.toUserDetails(dbUser)).thenReturn(userDetails);
        when(jwtUtil.isTokenValid("test-token", "alice")).thenReturn(true);

        jwtFilter.doFilterInternal(request, new MockHttpServletResponse(), noopChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).extracting(a -> a.getAuthority()).contains("ROLE_USER");
        assertThat(AuthenticatedUser.from(request)).containsSame(dbUser);
        verify(userService).loadUserForAuthentication("u1", "alice");
        verify(userService, never()).loadUserByUsername(anyString());
        verify(jwtUtil, never()).legacyTokenCouldBelongToUser(any(), any());
    }

    @Test
    void uidJwtIgnoresCreatedAtEvenWhenTokenIatIsOlder() throws Exception {
        MockHttpServletRequest request = requestWithBearer("/api/users/profile", "test-token");
        Date createdAt = new Date();
        Date issuedAt = new Date(createdAt.getTime() - 86_400_000L);
        User dbUser = dbUser("u1", "alice", "USER", true, createdAt);
        when(jwtUtil.extractIdentity("test-token")).thenReturn(identity("alice", "u1", "USER", issuedAt));
        when(userService.loadUserForAuthentication("u1", "alice")).thenReturn(dbUser);
        when(userService.toUserDetails(dbUser)).thenReturn(details("alice", "ROLE_USER", true));
        when(jwtUtil.isTokenValid("test-token", "alice")).thenReturn(true);

        jwtFilter.doFilterInternal(request, new MockHttpServletResponse(), noopChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(AuthenticatedUser.from(request)).containsSame(dbUser);
        verify(userService).loadUserForAuthentication("u1", "alice");
        verify(jwtUtil, never()).legacyTokenCouldBelongToUser(any(), any());
    }

    @Test
    void legacyJwtWithoutUidFallsBackToUsernameLookup() throws Exception {
        MockHttpServletRequest request = requestWithBearer("/api/users/stats", "test-token");
        Date createdAt = new Date(System.currentTimeMillis() - 60_000);
        Date issuedAt = new Date();
        User dbUser = dbUser("u1", "alice", "USER", true, createdAt);
        JwtUtil.JwtIdentity identity = identity("alice", null, "USER", issuedAt);
        when(jwtUtil.extractIdentity("test-token")).thenReturn(identity);
        when(userService.loadUserForAuthentication(null, "alice")).thenReturn(dbUser);
        when(userService.toUserDetails(dbUser)).thenReturn(details("alice", "ROLE_USER", true));
        when(jwtUtil.isTokenValid("test-token", "alice")).thenReturn(true);

        jwtFilter.doFilterInternal(request, new MockHttpServletResponse(), noopChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(AuthenticatedUser.from(request)).containsSame(dbUser);
        verify(userService).loadUserForAuthentication(null, "alice");
    }

    @Test
    void legacyJwtIssuedBeforeReplacementUserDoesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = requestWithBearer("/api/users/profile", "test-token");
        Date issuedAt = new Date(System.currentTimeMillis() - 86_400_000L);
        Date createdAt = new Date();
        User replacement = dbUser("u2", "alice", "USER", true, createdAt);
        JwtUtil.JwtIdentity identity = identity("alice", null, "USER", issuedAt);
        when(jwtUtil.extractIdentity("test-token")).thenReturn(identity);
        when(userService.loadUserForAuthentication(null, "alice")).thenReturn(replacement);
        when(userService.toUserDetails(replacement)).thenReturn(details("alice", "ROLE_USER", true));

        jwtFilter.doFilterInternal(request, new MockHttpServletResponse(), noopChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(AuthenticatedUser.from(request)).isEmpty();
        verify(userService).loadUserForAuthentication(null, "alice");
    }

    @Test
    void legacyJwtSameSecondAsUserCreationStillAuthenticates() throws Exception {
        MockHttpServletRequest request = requestWithBearer("/api/users/profile", "test-token");
        long secondFloor = (System.currentTimeMillis() / 1000L) * 1000L;
        Date issuedAt = new Date(secondFloor);
        Date createdAt = new Date(secondFloor + 400L);
        User dbUser = dbUser("u1", "alice", "USER", true, createdAt);
        JwtUtil.JwtIdentity identity = identity("alice", null, "USER", issuedAt);
        when(jwtUtil.extractIdentity("test-token")).thenReturn(identity);
        when(userService.loadUserForAuthentication(null, "alice")).thenReturn(dbUser);
        when(userService.toUserDetails(dbUser)).thenReturn(details("alice", "ROLE_USER", true));
        when(jwtUtil.isTokenValid("test-token", "alice")).thenReturn(true);

        jwtFilter.doFilterInternal(request, new MockHttpServletResponse(), noopChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(AuthenticatedUser.from(request)).containsSame(dbUser);
    }

    @Test
    void legacyJwtWithoutIssuedAtDoesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = requestWithBearer("/api/users/profile", "test-token");
        User dbUser = dbUser("u1", "alice", "USER", true, new Date());
        JwtUtil.JwtIdentity identity = identity("alice", null, "USER", null);
        when(jwtUtil.extractIdentity("test-token")).thenReturn(identity);
        when(userService.loadUserForAuthentication(null, "alice")).thenReturn(dbUser);
        when(userService.toUserDetails(dbUser)).thenReturn(details("alice", "ROLE_USER", true));

        jwtFilter.doFilterInternal(request, new MockHttpServletResponse(), noopChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(AuthenticatedUser.from(request)).isEmpty();
    }

    @Test
    void shouldReplaceAnonymousAuthenticationWithJwtAuthentication() throws Exception {
        MockHttpServletRequest request = requestWithBearer("/api/submissions/limits", "test-token");
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        ));
        Date createdAt = new Date(System.currentTimeMillis() - 60_000);
        User dbUser = dbUser("u1", "alice", "USER", true, createdAt);
        UserDetails userDetails = details("alice", "ROLE_USER", true);
        when(jwtUtil.extractIdentity("test-token")).thenReturn(identity("alice", null, "USER", new Date()));
        when(userService.loadUserForAuthentication(null, "alice")).thenReturn(dbUser);
        when(userService.toUserDetails(dbUser)).thenReturn(userDetails);
        when(jwtUtil.isTokenValid("test-token", "alice")).thenReturn(true);

        jwtFilter.doFilterInternal(request, new MockHttpServletResponse(), noopChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isSameAs(userDetails);
        assertThat(AuthenticatedUser.from(request)).containsSame(dbUser);
    }

    @Test
    void shouldTrimWhitespaceAroundBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/submissions/limits");
        request.addHeader("Authorization", "   Bearer   test-token   ");
        Date createdAt = new Date(System.currentTimeMillis() - 60_000);
        User dbUser = dbUser("u1", "alice", "USER", true, createdAt);
        UserDetails userDetails = details("alice", "ROLE_USER", true);
        when(jwtUtil.extractIdentity("test-token")).thenReturn(identity("alice", null, "USER", new Date()));
        when(userService.loadUserForAuthentication(null, "alice")).thenReturn(dbUser);
        when(userService.toUserDetails(dbUser)).thenReturn(userDetails);
        when(jwtUtil.isTokenValid("test-token", "alice")).thenReturn(true);

        jwtFilter.doFilterInternal(request, new MockHttpServletResponse(), noopChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(userDetails);
    }

    @Test
    void databaseRoleIsAuthoritative() throws Exception {
        MockHttpServletRequest request = requestWithBearer("/api/users/profile", "test-token");
        User dbUser = dbUser("u1", "alice", "ADMIN", true, new Date());
        when(jwtUtil.extractIdentity("test-token")).thenReturn(identity("alice", "u1", "USER", new Date()));
        when(userService.loadUserForAuthentication("u1", "alice")).thenReturn(dbUser);
        when(userService.toUserDetails(dbUser)).thenReturn(details("alice", "ROLE_ADMIN", true));
        when(jwtUtil.isTokenValid("test-token", "alice")).thenReturn(true);

        jwtFilter.doFilterInternal(request, new MockHttpServletResponse(), noopChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
            .extracting(a -> a.getAuthority())
            .contains("ROLE_ADMIN");
        verify(jwtUtil, never()).legacyTokenCouldBelongToUser(any(), any());
    }

    @Test
    void missingUserDoesNotLeaveAuthentication() throws Exception {
        MockHttpServletRequest request = requestWithBearer("/api/users/profile", "test-token");
        when(jwtUtil.extractIdentity("test-token")).thenReturn(identity("alice", "missing", "USER", new Date()));
        when(userService.loadUserForAuthentication("missing", "alice"))
            .thenThrow(new UsernameNotFoundException("gone"));
        jwtFilter.doFilterInternal(request, new MockHttpServletResponse(), noopChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(AuthenticatedUser.from(request)).isEmpty();
    }

    @Test
    void tokenForDeletedUserDoesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = requestWithBearer("/api/users/me/export", "test-token");
        when(jwtUtil.extractIdentity("test-token")).thenReturn(identity("alice", "u1", "USER", new Date()));
        when(userService.loadUserForAuthentication("u1", "alice"))
            .thenThrow(new UsernameNotFoundException("deleted"));
        jwtFilter.doFilterInternal(request, new MockHttpServletResponse(), noopChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(AuthenticatedUser.from(request)).isEmpty();
    }

    @Test
    void disabledUserDetailsAreNotAuthenticated() throws Exception {
        MockHttpServletRequest request = requestWithBearer("/api/users/profile", "test-token");
        User dbUser = dbUser("u1", "alice", "USER", false, new Date());
        when(jwtUtil.extractIdentity("test-token")).thenReturn(identity("alice", "u1", "USER", new Date()));
        when(userService.loadUserForAuthentication("u1", "alice")).thenReturn(dbUser);
        when(userService.toUserDetails(dbUser)).thenReturn(details("alice", "ROLE_USER", false));
        jwtFilter.doFilterInternal(request, new MockHttpServletResponse(), noopChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(AuthenticatedUser.from(request)).isEmpty();
    }

    @Test
    void invalidTokenDoesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = requestWithBearer("/api/users/profile", "bad-token");
        when(jwtUtil.extractIdentity("bad-token")).thenThrow(new RuntimeException("invalid"));
        jwtFilter.doFilterInternal(request, new MockHttpServletResponse(), noopChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(AuthenticatedUser.from(request)).isEmpty();
        verify(userService, never()).loadUserForAuthentication(any(), any());
    }

    private static MockHttpServletRequest requestWithBearer(String uri, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static FilterChain noopChain() {
        return (req, res) -> {};
    }

    private static JwtUtil.JwtIdentity identity(String username, String userId, String role, Date issuedAt) {
        return new JwtUtil.JwtIdentity(username, userId, role, issuedAt);
    }

    private static User dbUser(String id, String username, String role, boolean verified, Date createdAt) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setEmailVerified(verified);
        user.setCreatedAt(createdAt);
        return user;
    }

    private static UserDetails details(String username, String authority, boolean enabled) {
        return org.springframework.security.core.userdetails.User.withUsername(username)
            .password("password")
            .authorities(authority)
            .disabled(!enabled)
            .build();
    }
}
