package com.ldang03.common.dto;


import lombok.Data;

import java.util.List;

@Data
public class ProjectDto {

    private String projectId;
    private String projectName;
    private String shortName;
    private String projectDescription;
    private String projectManager;
    private List<String> jiraIssues;
}
