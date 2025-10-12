package com.brandedhustlers.electionbackend.auth.service;

import com.brandedhustlers.electionbackend.auth.dto.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface IAuthService {

    RegistrationResponse register(RegistrationRequest registrationRequest);

    LoginResponse login(LoginRequest loginRequest, HttpServletResponse response);

    CreateUserResponse createUser(CreateUserRequest createUserRequest, MultipartFile userImage) throws IOException;
}
