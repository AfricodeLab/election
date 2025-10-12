package com.brandedhustlers.electionbackend.auth.controller;

import com.brandedhustlers.electionbackend.auth.dto.CreateUserRequest;
import com.brandedhustlers.electionbackend.auth.dto.LoginRequest;
import com.brandedhustlers.electionbackend.auth.dto.RegistrationRequest;
import com.brandedhustlers.electionbackend.auth.service.AuthService;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final HttpServletRequest servletRequest;
    private final HttpServletResponse servletResponse;
    private final JWTService jwtService;

    public AuthController(AuthService authService,
                          HttpServletRequest request,
                          HttpServletResponse response,
                          JWTService jwtService) {
        this.authService = authService;
        this.servletRequest = request;
        this.servletResponse = response;
        this.jwtService = jwtService;
    }

    @PostMapping("/register-user")
    public ResponseEntity<?> registerUser(
            @Valid @RequestBody RegistrationRequest registrationRequest
            ){
        return new ResponseEntity<>(authService.register(registrationRequest), HttpStatus.OK);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request
    ){
        return  new ResponseEntity<>(authService.login(request, servletResponse), HttpStatus.CREATED);
    }

    @PostMapping("/create-user")
    public ResponseEntity<?> createUser(
            @Valid @RequestPart(name = "user") CreateUserRequest request,
            @RequestPart(name = "userImage", required = false)MultipartFile userImage
            ) throws IOException {
        return new ResponseEntity<>(authService.createUser(request, userImage), HttpStatus.CREATED)
    }
}
