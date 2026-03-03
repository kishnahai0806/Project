package com.campuseventhub.user.service;

import com.campuseventhub.common.exception.ConflictException;
import com.campuseventhub.security.CustomUserDetailsService;
import com.campuseventhub.security.JwtTokenProvider;
import com.campuseventhub.user.dto.AuthResponse;
import com.campuseventhub.user.dto.LoginRequest;
import com.campuseventhub.user.dto.RegisterRequest;
import com.campuseventhub.user.entity.Role;
import com.campuseventhub.user.entity.User;
import com.campuseventhub.user.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService customUserDetailsService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       AuthenticationManager authenticationManager,
                       CustomUserDetailsService customUserDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        user.setEnabled(true);

        User saved = userRepository.save(user);
        String token = jwtTokenProvider.generateToken(saved.getUsername(), saved.getRole());

        return new AuthResponse(token, "Bearer", jwtTokenProvider.getExpirationMs(), saved.getUsername(), saved.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        User user = customUserDetailsService.findDomainUserByUsername(request.getUsername());
        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());

        return new AuthResponse(token, "Bearer", jwtTokenProvider.getExpirationMs(), user.getUsername(), user.getRole().name());
    }
}
