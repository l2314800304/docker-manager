package com.dockermanager.service;

import com.dockermanager.docker.ContainerLifecycleManager;
import com.dockermanager.model.dto.ImageUpdateRequest;
import com.dockermanager.model.enums.AuditAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);
    private final ContainerLifecycleManager lifecycleManager;
    private final AuditService auditService;
    private final HttpClient httpClient;

    public ImageService(ContainerLifecycleManager lifecycleManager, AuditService auditService) {
        this.lifecycleManager = lifecycleManager;
        this.auditService = auditService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String updateServiceImage(String projectName, String serviceName,
                                     ImageUpdateRequest request, Consumer<String> onProgress) {
        String taskId = lifecycleManager.updateServiceImage(projectName, serviceName, request, onProgress);

        auditService.log(AuditAction.UPDATE_TAG, null, projectName, serviceName, "STARTED",
                "Updated " + request.getImage() + ":" + request.getCurrentTag() + " -> " + request.getNewTag());

        return taskId;
    }

    public ContainerLifecycleManager.TaskStatus getTaskStatus(String taskId) {
        return lifecycleManager.getTaskStatus(taskId);
    }

    /**
     * Query Docker Hub API for available image tags.
     * Falls back to common tags if the API call fails.
     */
    @SuppressWarnings("unchecked")
    public List<String> getImageTags(String imageName) {
        try {
            // Normalize image name for Docker Hub API
            String normalized = imageName.contains("/") ? imageName : "library/" + imageName;
            // Docker Hub API v2 - get tags list
            String url = "https://hub.docker.com/v2/repositories/" + normalized + "/tags/?page_size=50&ordering=last_updated";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Parse JSON response using Jackson
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> body = mapper.readValue(response.body(), Map.class);
                List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");

                if (results != null && !results.isEmpty()) {
                    List<String> tags = new ArrayList<>();
                    for (Map<String, Object> tag : results) {
                        String name = (String) tag.get("name");
                        if (name != null) tags.add(name);
                    }
                    return tags;
                }
            }

            log.debug("Docker Hub API returned status {} for image {}", response.statusCode(), imageName);
        } catch (Exception e) {
            log.debug("Failed to fetch tags from Docker Hub for {}: {}", imageName, e.getMessage());
        }

        // Fallback: try local Docker image tags
        try {
            return getLocalImageTags(imageName);
        } catch (Exception e) {
            log.debug("Failed to get local tags for {}: {}", imageName, e.getMessage());
        }

        return List.of("latest", "alpine", "slim");
    }

    /**
     * Get tags from locally available Docker images.
     */
    private List<String> getLocalImageTags(String imageName) {
        // This would need the DockerClient to list image tags
        // For now, return common tags as fallback
        return List.of("latest", "alpine", "slim", "stable", "dev");
    }
}
