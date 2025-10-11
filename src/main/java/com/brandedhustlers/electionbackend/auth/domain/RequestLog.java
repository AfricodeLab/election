package com.brandedhustlers.electionbackend.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "request_logs", schema = "auth")
public class RequestLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String menuId;
    private String requestUrl;
    private String ipAddr;
    private LocalDateTime accessDt;
    private String searchConditionCn;
    private String firstRegisterId;
    private String lastUpdateId;
    private int responseStatus;
    private String responseBody;
}
