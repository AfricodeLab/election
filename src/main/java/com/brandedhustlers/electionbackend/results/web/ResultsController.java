package com.brandedhustlers.electionbackend.results.web;

import com.brandedhustlers.electionbackend.results.service.ResultsService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/results")
public class ResultsController {
    private final ResultsService service;

    public ResultsController(ResultsService service) {
        this.service = service;
    }

    @GetMapping("/election/{electionId}")
    public ResponseEntity<?> get(@PathVariable UUID electionId, Pageable pageable) {
        return new ResponseEntity<>(service.get(electionId, pageable), HttpStatus.OK);
    }
}