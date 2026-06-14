package com.dockermanager.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 镜像更新请求 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUpdateRequest {
    private String image;
    private String currentTag;
    private String newTag;
    private boolean autoRestart;
    private boolean rollbackOnFailure;
}
