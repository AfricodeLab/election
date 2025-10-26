package com.brandedhustlers.electionbackend.audit.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T> {
    private List<T> items;
    private int page;
    private int size;
    private long total;
}

