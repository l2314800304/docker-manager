package com.dockermanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
