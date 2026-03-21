package com.marketplace.backend.services;

import com.marketplace.backend.dtos.AuthenticationRequest;
import com.marketplace.backend.dtos.AuthenticationResponse;
import com.marketplace.backend.dtos.RegisterRequest;
import com.marketplace.backend.dtos.UserDto;
import com.marketplace.backend.entities.Account;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.enums.AccountType;
import com.marketplace.backend.enums.Role;
import com.marketplace.backend.repositories.AccountRepository;
import com.marketplace.backend.repositories.UserRepository;
import com.marketplace.backend.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AccountRepository accountRepository;


    @Transactional
    public AuthenticationResponse register(RegisterRequest request, HttpServletResponse response) {
        String cleanPhone = normalizePhoneNumber(request.phoneNumber());
        if (userRepository.findByPhoneNumber(cleanPhone).isPresent()) {
            throw new RuntimeException("Email already registered");
        }
        String[] nameParts = request.fullName().trim().split(" ", 2);
        var user = User.builder()
                .firstName(nameParts[0])
                .lastName(nameParts.length > 1 ? nameParts[1] : "")
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .phoneNumber(cleanPhone)
                .subscribedMerchants(new HashSet<>())
                .build();
        if (request.referralMerchantId() != null && !request.referralMerchantId().isBlank() && request.role() == Role.CUSTOMER) {
            User merchant = userRepository.findById(UUID.fromString(request.referralMerchantId()))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid Merchant QR Code."));
            user.subscribeToMerchant(merchant);
        }

        if (request.role() == Role.MERCHANT) {
            if (request.referralMerchantId() != null && !request.referralMerchantId().isBlank()) {
                throw new IllegalArgumentException("Merchants cannot be linked to other merchants.");
            }
        } else {
            if (request.referralMerchantId() != null && !request.referralMerchantId().isBlank()) {
                User merchant = userRepository.findById(UUID.fromString(request.referralMerchantId()))
                        .orElseThrow(() -> new IllegalArgumentException("Invalid Merchant QR Code."));
                user.subscribeToMerchant(merchant);
            }
        }

        var savedUser = userRepository.save(user);
        if (user.getRole() == Role.MERCHANT || user.getRole() == Role.DISTRIBUTOR) {
            Account userAccount = Account.builder()
                    .name(savedUser.getId() + "_" + savedUser.getRole().name() + "_PAYABLE")
                    .type(AccountType.LIABILITY)
                    .balance(BigDecimal.ZERO)
                    .owner(savedUser)
                    .build();
            accountRepository.save(userAccount);
        }
        return createTokenResponse(savedUser, response);
    }

    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request, HttpServletResponse response) {
        String cleanPhone = normalizePhoneNumber(request.phoneNumber());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(cleanPhone, request.password())
        );

        log.info("Spring Security password check passed for: {}", cleanPhone);

        var user = userRepository.findByPhoneNumber(cleanPhone)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.referralMerchantId() != null && !request.referralMerchantId().isBlank()) {
            User merchant = userRepository.findById(UUID.fromString(request.referralMerchantId()))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid Merchant QR Code."));

            if (user.getRole() == Role.CUSTOMER || user.getRole() == Role.DISTRIBUTOR) {
                user.subscribeToMerchant(merchant);
                userRepository.save(user);
                log.info("Existing user {} linked to new Merchant {} during login.", user.getId(), merchant.getId());
            }
        }

        return createTokenResponse(user, response);
    }

    public AuthenticationResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() == null) {
            throw new RuntimeException("No cookies found");
        }

        String refreshToken = Arrays.stream(request.getCookies())
                .filter(cookie -> "refresh_token".equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElseThrow(() -> new RuntimeException("Refresh token missing"));

        String userEmail = jwtService.extractUsername(refreshToken);
        var user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!jwtService.isTokenValid(refreshToken, user, "REFRESH")) {
            throw new RuntimeException("Invalid refresh token");
        }

        return createTokenResponse(user, response);
    }

    private AuthenticationResponse createTokenResponse(User user, HttpServletResponse response) {
        String accessToken = jwtService.generateToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        Cookie cookie = new Cookie("refresh_token", newRefreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // Must be true in production (HTTPS)
        cookie.setPath("/api/v1/auth/refresh"); // Scoped for security
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
        cookie.setAttribute("SameSite", "Strict"); // Prevent CSRF
        response.addCookie(cookie);

        String fullName = user.getFirstName() + " " + user.getLastName();
        UserDto userDto = new UserDto(user.getId(), fullName, user.getEmail(), user.getRole().name());

        return new AuthenticationResponse(accessToken, userDto);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie("refresh_token", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/v1/auth/refresh");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);

        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            jwtService.blacklistToken(jwt);
            log.info("Access token successfully blacklisted during logout.");
        }
    }

    private String normalizePhoneNumber(String rawPhone) {
        if (rawPhone == null) return null;
        String clean = rawPhone.replaceAll("[^0-9+]", "");

        if (clean.startsWith("+254")) {
            return "0" + clean.substring(4);
        } else if (clean.startsWith("254")) {
            return "0" + clean.substring(3);
        }
        return clean;
    }


}
