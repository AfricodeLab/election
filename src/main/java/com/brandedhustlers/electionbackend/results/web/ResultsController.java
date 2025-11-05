package com.brandedhustlers.electionbackend.results.web;

import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.results.service.ResultsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/results")
public class ResultsController {
    private final ResultsService service;
    private final HttpServletRequest httpServletRequest;
    private final JWTService jwtService;

    public ResultsController(ResultsService service,
                             HttpServletRequest httpServletRequest,
                             JWTService jwtService) {
        this.service = service;
        this.httpServletRequest = httpServletRequest;
        this.jwtService = jwtService;
    }

    @GetMapping("/election/{electionId}")
    public ResponseEntity<?> get(@PathVariable UUID electionId, Pageable pageable) {
        String userToken = httpServletRequest.getHeader("Authorization").substring(7);
        UUID appUserId = jwtService.extractAppUserId(userToken);

        return new ResponseEntity<>(service.get(appUserId, electionId, pageable), HttpStatus.OK);
    }
}