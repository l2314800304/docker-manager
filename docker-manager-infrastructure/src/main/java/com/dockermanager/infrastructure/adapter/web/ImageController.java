package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.domain.dto.ImageUpdateRequest;
import com.dockermanager.domain.port.inbound.DockerOperationPort;
import com.dockermanager.domain.util.ParamValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ImageController {

    private final DockerOperationPort dockerOperationPort;

    public ImageController(DockerOperationPort dockerOperationPort) {
        this.dockerOperationPort = dockerOperationPort;
    }

    @GetMapping("/images/{imageName}/tags")
    public List<String> getImageTags(@PathVariable String imageName) {
        ParamValidator.requireImageName(imageName, "无效的镜像名称");
        return dockerOperationPort.getImageTags(imageName);
    }

    @PostMapping("/projects/{projectName}/services/{serviceName}/update")
    public ResponseEntity<Map<String, String>> updateService(
            @PathVariable String projectName, @PathVariable String serviceName,
            @RequestBody ImageUpdateRequest request) {
        ParamValidator.requireNotBlank(projectName, "项目名不能为空");
        ParamValidator.requireNotBlank(serviceName, "服务名不能为空");
        ParamValidator.requireNotNull(request, "请求体不能为空");
        ParamValidator.requireNotBlank(request.getNewTag(), "新Tag不能为空");
        String taskId = dockerOperationPort.updateServiceImage(projectName, serviceName, request);
        return ResponseEntity.accepted().body(Map.of("taskId", taskId, "message", "Update started"));
    }

    @GetMapping("/tasks/{taskId}/status")
    public ResponseEntity<Map<String, String>> getTaskStatus(@PathVariable String taskId) {
        ParamValidator.requireNotBlank(taskId, "无效的任务ID");
        Map<String, String> status = dockerOperationPort.getTaskStatus(taskId);
        if (status == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(status);
    }
}
