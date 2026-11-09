package com.banking.core.config;

import com.banking.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Application-level bean configuration.
 *
 * <p>Separated from {@link SecurityConfig} to prevent circular dependency:
 * {@code SecurityConfig} depends on {@link UserDetailsService}, which depends
 * on {@link UserRepository}.  Keeping it in a separate {@code @Configuration}
 * class breaks the cycle cleanly.
 */
@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final UserRepository userRepository;

    /**
     * Custom {@link UserDetailsService} that loads a {@link com.banking.core.domain.entity.User}
     * from PostgreSQL by email.
     *
     * <p>Our {@code User} entity already implements {@code UserDetails}, so no
     * adapter or wrapper class is needed — we return it directly.
     *
     * @return a {@link UserDetailsService} lambda backed by {@link UserRepository}
     * @throws UsernameNotFoundException if no user with the given email exists
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByEmail(username)
            .orElseThrow(() ->
                new UsernameNotFoundException("User not found with email: " + username)
            );
    }
}
