package com.dockermanager.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 容器文件系统条目 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileEntryDTO {
    private String name;
    private String path;
    private String type; // file, directory, link
    private long size;
    private String permissions;
    private String owner;
    private String modifiedTime;
}
