package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.domain.dto.FileEntryDTO;
import com.dockermanager.domain.port.inbound.DockerOperationPort;
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
        try { return ResponseEntity.ok(dockerOperationPort.listDirectory(id, path)); }
        catch (Exception e) { return ResponseEntity.badRequest().build(); }
    }

    @GetMapping("/file")
    public ResponseEntity<Map<String, Object>> readFile(@PathVariable String id, @RequestParam String path) {
        try {
            String content = dockerOperationPort.readFile(id, path);
            if (content == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(Map.of("content", content, "path", path, "size", content.length()));
        } catch (Exception e) { return ResponseEntity.badRequest().build(); }
    }
}
