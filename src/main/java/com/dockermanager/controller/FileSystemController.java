package com.dockermanager.controller;

import com.dockermanager.model.dto.FileEntryDTO;
import com.dockermanager.service.FileSystemService;
import org.springframework.core.io.InputStreamResource;
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

    private final FileSystemService fileSystemService;

    public FileSystemController(FileSystemService fileSystemService) {
        this.fileSystemService = fileSystemService;
    }

    @GetMapping
    public ResponseEntity<List<FileEntryDTO>> listDirectory(
            @PathVariable String id,
            @RequestParam(defaultValue = "/") String path) {
        try {
            List<FileEntryDTO> entries = fileSystemService.listDirectory(id, path);
            return ResponseEntity.ok(entries);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/file")
    public ResponseEntity<Map<String, Object>> readFile(
            @PathVariable String id,
            @RequestParam String path) {
        try {
            String content = fileSystemService.readFile(id, path);
            if (content == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(Map.of(
                    "content", content,
                    "path", path,
                    "size", content.length()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @PathVariable String id,
            @RequestParam String path) {
        try {
            InputStream is = fileSystemService.downloadFile(id, path);
            String filename = path.substring(path.lastIndexOf('/') + 1);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new InputStreamResource(is));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
