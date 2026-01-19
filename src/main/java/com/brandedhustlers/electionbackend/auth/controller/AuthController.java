package com.brandedhustlers.electionbackend.auth.controller;

import com.brandedhustlers.electionbackend.auth.dto.CreateUserRequest;
import com.brandedhustlers.electionbackend.auth.dto.LoginRequest;
import com.brandedhustlers.electionbackend.auth.dto.RegistrationRequest;
import com.brandedhustlers.electionbackend.auth.mapper.AppUserMapper;
import com.brandedhustlers.electionbackend.auth.service.AuthService;
import com.brandedhustlers.electionbackend.common.config.storage.service.CloudinaryService;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final HttpServletResponse servletResponse;
    private final ObjectMapper objectMapper;
    private final GetUser getUser;
    private final AppUserMapper appUserMapper;
    private final CloudinaryService cloudinaryService;


    public AuthController(AuthService authService,
                          HttpServletResponse response,
                          ObjectMapper objectMapper, GetUser getUser,
                          AppUserMapper appUserMapper, CloudinaryService cloudinaryService) {
        this.authService = authService;
        this.servletResponse = response;
        this.objectMapper = objectMapper;
        this.getUser = getUser;
        this.appUserMapper = appUserMapper;
        this.cloudinaryService = cloudinaryService;
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
            @Valid @RequestBody CreateUserRequest request){
        return new ResponseEntity<>(authService.createUser(request), HttpStatus.CREATED);
    }

    @GetMapping("/get-users")
    public ResponseEntity<?> getOrgUser(Pageable pageable){
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        return new ResponseEntity<>(authService.getUsers(orgId, pageable), HttpStatus.OK);
    }

    @DeleteMapping("/deactivate/{userId}")
    public ResponseEntity<?> deactivateUser(@PathVariable UUID userId){
        authService.deactivateUser(userId);

        return ResponseEntity.ok("User with ID: " + userId + " deactivated.");
    }
    @DeleteMapping("/activate/{userId}")
    public ResponseEntity<?> activateUser(@PathVariable UUID userId){
        authService.activateUser(userId);

        return ResponseEntity.ok("User with ID: " + userId + " activated.");
    }

    @PatchMapping("/password/change")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordCmd changePasswordCmd){
        authService.changePassword(changePasswordCmd);
        return new ResponseEntity<>(new ChangePasswordResponse(true, "Password changed successfully"), HttpStatus.OK);
    }

    @PostMapping("/video")
    public ResponseEntity<?> video(@RequestPart(name = "video") MultipartFile video){
        cloudinaryService.uploadFile(video);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    public record ChangePasswordCmd(String oldPassword, String newPassword){}
    record ChangePasswordResponse(boolean success, String message){} //for anyone going through this codebase, I put the records here because I really didn't see why I should create a different file for them...since they'd only ever be use here...although the first one was use in the service...but yeah, you get me
}
