package com.congobs.loginregister.services;

import com.congobs.loginregister.dto.RegistrationDto;
import com.congobs.loginregister.models.ApplicationUser;
import com.congobs.loginregister.models.Token;
import com.congobs.loginregister.models.UserRole;
import com.congobs.loginregister.repositories.TokenRepository;
import com.congobs.loginregister.repositories.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private static final String CONFIRMATION_URL = "http://localhost:8080/api/v1/auth/confirm?token=%s";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRepository tokenRepository;
    private final EmailService emailService;

    @Transactional
    public String register(RegistrationDto registrationDto) {

        // check if user already exist
        boolean userExists = userRepository.findByEmail(registrationDto.getEmail()).isPresent();

        if (userExists) {
            throw new IllegalStateException("User with this email already exists");
        }

        // encode the password
        String encodedPassword = passwordEncoder.encode(registrationDto.getPassword());

        // transform - map (Converter) the RegistrationDto to ApplicationUser
        ApplicationUser applicationUser = getApplicationUser(registrationDto, encodedPassword);

        // save the user
        ApplicationUser savedUser = userRepository.save(applicationUser);

        // generate a token
        String generatedToken = UUID.randomUUID().toString();
        Token token = generateAndBuildToken(generatedToken, savedUser);

        tokenRepository.save(token);

        // Send the confirmation email
        try {
            emailService.send(
                registrationDto.getEmail(),
                registrationDto.getFirstname(),
                null,
                String.format(CONFIRMATION_URL, generatedToken)
            );
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }

        // return the success message
        return generatedToken;
    }

    public String confirm(String token) {

        Token savedToken = tokenRepository.findByToken(token).orElseThrow(
            () -> new IllegalStateException("Token not found")
        );

        if (LocalDateTime.now().isAfter(savedToken.getExpiresAt())) {
            // Generate and Resend new Token
            String generatedToken = UUID.randomUUID().toString();
            Token newToken = generateAndBuildToken(generatedToken, savedToken.getUser());

            tokenRepository.save(newToken);
             try {
                 emailService.send(
                     savedToken.getUser().getEmail(),
                     savedToken.getUser().getFirstname(),
                     null,
                     String.format(CONFIRMATION_URL, generatedToken)
                 );
             } catch (MessagingException e) {
                 throw new RuntimeException(e);
             }
            return "Token expired! A new Token hast been sent to your email";
        }

        ApplicationUser user = userRepository.findById(savedToken.getId()).orElseThrow(
            () -> new UsernameNotFoundException("User not found")
        );
        user.setEnabled(true);
        userRepository.save(user);

        savedToken.setValidatedAt(LocalDateTime.now());
        tokenRepository.save(savedToken);

        return "<h1>Your account has been successfully confirmed</h1>";
    }

    private ApplicationUser getApplicationUser(RegistrationDto registrationDto, String encodedPassword) {
        return ApplicationUser.builder()
                              .firstname(registrationDto.getFirstname())
                              .lastname(registrationDto.getLastname())
                              .email(registrationDto.getEmail())
                              .password(encodedPassword)
                              .role(UserRole.ROLE_USER)
                              .build();
    }
    private Token generateAndBuildToken(String generatedToken, ApplicationUser user) {
        return Token.builder()
                    .token(generatedToken)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .user(user)
                    .build();
    }

}
