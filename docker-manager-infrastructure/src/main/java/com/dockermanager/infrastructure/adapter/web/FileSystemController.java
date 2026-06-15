package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.domain.dto.FileEntryDTO;
import com.dockermanager.domain.port.inbound.DockerOperationPort;
import com.dockermanager.domain.util.ParamValidator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/containers/{id}/fs")
public class FileSystemController {

    private final DockerOperationPort dockerOperationPort;

    public FileSystemController(DockerOperationPort dockerOperationPort) {
        this.dockerOperationPort = dockerOperationPort;
    }

    @GetMapping
    public ResponseEntity<List<FileEntryDTO>> listDirectory(@PathVariable String id,
                                                             @RequestParam(defaultValue = "/") String path) {
        ParamValidator.requireContainerId(id, "无效的容器ID");
        ParamValidator.requireSafePath(path, "非法的文件路径");
        try { return ResponseEntity.ok(dockerOperationPort.listDirectory(id, path)); }
        catch (Exception e) { return ResponseEntity.badRequest().build(); }
    }

    @GetMapping("/file")
    public ResponseEntity<Map<String, Object>> readFile(@PathVariable String id, @RequestParam String path) {
        ParamValidator.requireContainerId(id, "无效的容器ID");
        ParamValidator.requireSafePath(path, "非法的文件路径");
        try {
            String content = dockerOperationPort.readFile(id, path);
            if (content == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(Map.of("content", content, "path", path, "size", content.length()));
        } catch (Exception e) { return ResponseEntity.badRequest().build(); }
    }
}
