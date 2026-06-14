package com.dockermanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
