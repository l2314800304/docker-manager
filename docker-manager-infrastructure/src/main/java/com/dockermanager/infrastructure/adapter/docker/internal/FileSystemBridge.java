package com.dockermanager.infrastructure.adapter.docker.internal;

import com.dockermanager.domain.dto.FileEntryDTO;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 容器文件系统桥接器。
 *
 * <p>提供浏览容器内文件目录和读取文件内容的能力。使用两种访问策略：</p>
 *
 * <h3>策略 1: Docker Exec（首选）</h3>
 * <ul>
 *   <li>通过 {@code docker exec} 在容器内执行 {@code ls -la} 或 {@code cat} 命令</li>
 *   <li>优点：速度快，信息完整（权限、所有者、修改时间）</li>
 *   <li>限制：需要容器内有 ls/cat 命令（大多数基础镜像都有）</li>
 * </ul>
 *
 * <h3>策略 2: Docker Copy Archive（降级方案）</h3>
 * <ul>
 *   <li>通过 {@code docker cp} API 将文件/目录以 tar 归档格式复制到宿主机</li>
 *   <li>优点：不依赖容器内的命令</li>
 *   <li>限制：信息不完整（无权限、修改时间），只能读取不能列目录</li>
 * </ul>
 *
 * <h3>ls 输出解析：</h3>
 * <p>使用正则表达式解析 {@code ls -la --time-style=long-iso} 的输出格式：</p>
 * <pre>
 * drwxr-xr-x 2 root root 4096 2024-01-15 10:30 mydir
 * -rw-r--r-- 1 root root 1234 2024-01-15 10:30 myfile.txt
 * lrwxrwxrwx 1 root root    7 2024-01-15 10:30 link -> target
 * </pre>
 *
 * @see FileEntryDTO 文件条目数据传输对象
 * @see DockerClientBridge#execInContainer(String, String...) 容器内命令执行
 */
@Component
public class FileSystemBridge {

    private static final Logger log = LoggerFactory.getLogger(FileSystemBridge.class);

    /**
     * ls -la 输出线的正则解析模式。
     *
     * <p>捕获组：</p>
     * <ol>
     *   <li>权限字符串（如 "drwxr-xr-x"）</li>
     *   <li>硬链接数</li>
     *   <li>所有者名</li>
     *   <li>所属组名</li>
     *   <li>文件大小（字节）</li>
     *   <li>修改时间（"YYYY-MM-DD HH:MM"）</li>
     *   <li>文件名（可能包含 " -> target" 的符号链接）</li>
     * </ol>
     */
    private static final Pattern LS_PATTERN = Pattern.compile(
            "^([drwxlsStT-]{10})\\s+(\\d+)\\s+(\\S+)\\s+(\\S+)\\s+(\\d+)\\s+(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})\\s+(.+)$"
    );

    private final DockerClientBridge dockerClient;

    public FileSystemBridge(DockerClientBridge dockerClient) {
        this.dockerClient = dockerClient;
    }

    /**
     * 列出容器内指定目录的文件和子目录。
     *
     * <p>优先使用 exec + ls 方式；如果失败（如容器内没有 ls 命令），
     * 自动降级为 tar 归档方式。</p>
     *
     * @param containerId 容器 ID
     * @param path        目录路径（null 或空时使用 "/"）
     * @return 文件条目列表（不含 "." 和 ".."）
     */
    public List<FileEntryDTO> listDirectory(String containerId, String path) {
        String targetPath = path == null || path.isBlank() ? "/" : path;
        try {
            // 策略 1: 通过 exec 执行 ls 命令
            String output = dockerClient.execInContainer(containerId,
                    "ls", "-la", "--time-style=long-iso", targetPath);

            List<FileEntryDTO> entries = new ArrayList<>();
            String[] lines = output.split("\n");

            for (String line : lines) {
                // 跳过 "total xxx" 汇总行和空行
                if (line.startsWith("total") || line.isBlank()) continue;

                Matcher matcher = LS_PATTERN.matcher(line.trim());
                if (matcher.matches()) {
                    String permissions = matcher.group(1);   // 权限: drwxr-xr-x
                    String owner = matcher.group(3);          // 所有者: root
                    long size = Long.parseLong(matcher.group(5));  // 大小: 4096
                    String modifiedTime = matcher.group(6);    // 时间: 2024-01-15 10:30
                    String name = matcher.group(7);            // 名称: myfile.txt

                    // 处理符号链接："name -> target" 格式，只取链接名
                    if (name.contains(" -> ")) {
                        name = name.split(" -> ")[0];
                    }

                    // 根据权限字符串首字符判断文件类型
                    String type;
                    char typeChar = permissions.charAt(0);
                    if (typeChar == 'd') type = "directory";
                    else if (typeChar == 'l') type = "link";
                    else type = "file";

                    // 跳过当前目录和父目录
                    if (".".equals(name) || "..".equals(name)) continue;

                    // 拼接完整的文件路径
                    String fullPath = targetPath.endsWith("/")
                            ? targetPath + name
                            : targetPath + "/" + name;

                    entries.add(FileEntryDTO.builder()
                            .name(name)
                            .path(fullPath)
                            .type(type)
                            .size(size)
                            .permissions(permissions)
                            .owner(owner)
                            .modifiedTime(modifiedTime)
                            .build());
                }
            }
            return entries;

        } catch (Exception e) {
            // exec 方式失败（可能容器内没有 ls），降级为 tar 方式
            log.warn("Failed to list directory {} in container {}: {}, trying tar fallback",
                    targetPath, containerId, e.getMessage());
            return listDirectoryViaTar(containerId, targetPath);
        }
    }

    /**
     * 读取容器内指定文件的内容。
     *
     * <p>优先使用 exec + cat 方式；如果失败，降级为 tar 归档方式。</p>
     *
     * @param containerId 容器 ID
     * @param filePath    文件在容器内的绝对路径
     * @return 文件的 UTF-8 文本内容，读取失败时返回 null
     */
    public String readFile(String containerId, String filePath) {
        try {
            // 策略 1: 通过 exec 执行 cat 命令
            String output = dockerClient.execInContainer(containerId, "cat", filePath);
            return output;
        } catch (Exception e) {
            log.warn("Failed to read file {} via exec: {}", filePath, e.getMessage());
            return readFileViaTar(containerId, filePath);
        }
    }

    /**
     * 下载容器内的文件（返回原始流）。
     *
     * <p>直接使用 Docker copy-archive API，返回 tar 格式的 InputStream。
     * 调用方可直接将其作为下载响应返回给前端。</p>
     *
     * @param containerId 容器 ID
     * @param filePath    文件路径
     * @return tar 归档格式的输入流
     */
    public InputStream downloadFile(String containerId, String filePath) {
        return dockerClient.copyArchiveFromContainer(containerId, filePath);
    }

    /**
     * 通过 tar 归档方式列出目录内容（降级方案）。
     *
     * <p>使用 Docker 的 copy-archive API 将目录打包为 tar 流，
     * 然后解析 tar 条目获取文件列表。</p>
     *
     * <p>限制：无法获取权限和修改时间信息。</p>
     *
     * @param containerId 容器 ID
     * @param path        目录路径
     * @return 文件条目列表
     */
    private List<FileEntryDTO> listDirectoryViaTar(String containerId, String path) {
        List<FileEntryDTO> entries = new ArrayList<>();
        try (InputStream is = dockerClient.copyArchiveFromContainer(containerId, path);
             TarArchiveInputStream tar = new TarArchiveInputStream(is)) {

            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                String name = entry.getName();
                // tar 条目可能包含路径前缀，只取文件名
                if (name.contains("/")) {
                    name = name.substring(name.lastIndexOf('/') + 1);
                }
                if (name.isEmpty() || ".".equals(name) || "..".equals(name)) continue;

                String type;
                if (entry.isDirectory()) type = "directory";
                else if (entry.isSymbolicLink()) type = "link";
                else type = "file";

                entries.add(FileEntryDTO.builder()
                        .name(name)
                        .path(path.endsWith("/") ? path + name : path + "/" + name)
                        .type(type)
                        .size(entry.getSize())
                        .owner(entry.getUserName())
                        .build());
            }
        } catch (Exception e) {
            log.error("Tar fallback also failed for {} in {}: {}", path, containerId, e.getMessage());
        }
        return entries;
    }

    /**
     * 通过 tar 归档方式读取文件内容（降级方案）。
     *
     * <p>将文件以 tar 格式从容器复制出来，解压后读取内容。</p>
     *
     * @param containerId 容器 ID
     * @param filePath    文件路径
     * @return 文件的 UTF-8 文本内容，失败返回 null
     */
    private String readFileViaTar(String containerId, String filePath) {
        try (InputStream is = dockerClient.copyArchiveFromContainer(containerId, filePath);
             TarArchiveInputStream tar = new TarArchiveInputStream(is)) {

            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (!entry.isDirectory()) {
                    byte[] bytes = tar.readNBytes((int) entry.getSize());
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.error("Tar fallback also failed for file {} in {}: {}", filePath, containerId, e.getMessage());
        }
        return null;
    }
}
