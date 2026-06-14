package com.dockermanager.service;

import com.dockermanager.docker.ContainerFileSystemAccessor;
import com.dockermanager.model.dto.FileEntryDTO;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
public class FileSystemService {

    private final ContainerFileSystemAccessor fileSystemAccessor;

    public FileSystemService(ContainerFileSystemAccessor fileSystemAccessor) {
        this.fileSystemAccessor = fileSystemAccessor;
    }

    public List<FileEntryDTO> listDirectory(String containerId, String path) {
        return fileSystemAccessor.listDirectory(containerId, path);
    }

    public String readFile(String containerId, String filePath) {
        return fileSystemAccessor.readFile(containerId, filePath);
    }

    public InputStream downloadFile(String containerId, String filePath) {
        return fileSystemAccessor.downloadFile(containerId, filePath);
    }
}
