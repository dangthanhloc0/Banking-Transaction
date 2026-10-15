package com.ldang03.common.dto;


import com.ldang03.common.constants.LastRunStatus;
import com.ldang03.common.constants.RunLockStatus;
import com.ldang03.common.constants.TargetType;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class ScheduleConfigResponse {

    private String id;
    private String name;

    private ProjectDto project;

    private TargetType targetType;
    private List<String> testCaseIds;
    private int testCaseCount;
    private List<String> ftpFolderIds;

    private String cronExpression;
    private String timezone;
    private String environment;

    private int numberConcurrent;

    private boolean enabled;

    private RunLockStatus status;
    private Instant lockedUntil;

    private Instant nextRunAt;
    private Instant lastRunAt;
    private LastRunStatus lastRunStatus;
    private String lastRunDetail;

    private boolean emailNotificationEnabled;
    private List<String> notifyEmails;

    private String description;

    private Instant createdAt;
    private Instant updatedAt;

    private  ScheduleConfigResponse scheduleConfigResponse;
}



