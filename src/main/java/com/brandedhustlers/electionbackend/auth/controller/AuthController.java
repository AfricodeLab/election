package com.brandedhustlers.electionbackend.auth.controller;

import com.brandedhustlers.electionbackend.auth.dto.CreateUserRequest;
import com.brandedhustlers.electionbackend.auth.dto.LoginRequest;
import com.brandedhustlers.electionbackend.auth.dto.RegistrationRequest;
import com.brandedhustlers.electionbackend.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final HttpServletResponse servletResponse;
    private final ObjectMapper objectMapper;

    public AuthController(AuthService authService,
                          HttpServletResponse response,
                          ObjectMapper objectMapper) {
        this.authService = authService;
        this.servletResponse = response;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/register-user")
    public ResponseEntity<?> registerUser(
            @Valid @RequestBody RegistrationRequest registrationRequest
            ){
        return new ResponseEntity<>(authService.register(registrationRequest), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request
    ){
        return  new ResponseEntity<>(authService.login(request, servletResponse), HttpStatus.OK);
    }

    @PostMapping("/create-user")
    public ResponseEntity<?> createUser(
            @Valid @RequestPart(name = "user") String request,
            @RequestPart(name = "userImage", required = false)MultipartFile userImage
            ) throws IOException {
        CreateUserRequest request1 = objectMapper.readValue(request,CreateUserRequest.class);
        return new ResponseEntity<>(authService.createUser(request1, userImage), HttpStatus.CREATED);
    }
}
