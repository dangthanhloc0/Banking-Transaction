package com.ldang03.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RunResultPayload {
    private String lastRunStatus; // "PASSED" | "FAILED" | "ERROR"
    private String lastRunDetail;
    private Instant nextRunAt;
}
