package com.coderzclub.config;

import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

public final class AuthenticatedUser {
    public static final String ATTRIBUTE = "authenticatedUser";

    private AuthenticatedUser() {}

    public static void set(HttpServletRequest request, User user) {
        if (request != null && user != null) {
            request.setAttribute(ATTRIBUTE, user);
        }
    }

    public static Optional<User> from(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof User user ? Optional.of(user) : Optional.empty();
    }

    public static Optional<User> current(UserRepository userRepository) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(auth)) {
            return Optional.empty();
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            Optional<User> fromRequest = from(servletAttributes.getRequest());
            if (fromRequest.isPresent()) {
                return fromRequest;
            }
        }
        if (userRepository == null) {
            return Optional.empty();
        }
        return userRepository.findByUsername(auth.getName());
    }

    private static boolean isAuthenticated(Authentication auth) {
        return auth != null
            && auth.isAuthenticated()
            && auth.getName() != null
            && !"anonymousUser".equals(auth.getName())
            && !(auth instanceof AnonymousAuthenticationToken);
    }
}
