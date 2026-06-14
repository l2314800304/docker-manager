package com.dockermanager.docker;

import com.dockermanager.model.dto.ComposeProjectDTO;
import com.dockermanager.model.dto.ContainerInfoDTO;
import com.dockermanager.model.enums.ContainerState;
import com.github.dockerjava.api.model.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Docker Compose 项目发现服务。
 *
 * <p>通过扫描 Docker daemon 中所有容器的 label 信息，自动发现和分组
 * docker-compose 创建的项目及其服务。</p>
 *
 * <h3>发现原理：</h3>
 * <p>docker-compose 在创建容器时会自动添加以下 label：</p>
 * <ul>
 *   <li>{@code com.docker.compose.project} — 项目名称（用于分组）</li>
 *   <li>{@code com.docker.compose.service} — 服务名（docker-compose.yml 中的 service 名）</li>
 *   <li>{@code com.docker.compose.project.working_dir} — compose 文件所在目录</li>
 * </ul>
 *
 * <h3>核心功能：</h3>
 * <ul>
 *   <li>{@link #discoverProjects()} — 扫描所有 Compose 项目</li>
 *   <li>{@link #getProject(String)} — 获取指定项目详情</li>
 *   <li>{@link #toContainerInfoDTO(Container)} — 将 Docker 容器对象转换为业务 DTO</li>
 * </ul>
 *
 * @see ComposeProjectDTO 项目数据传输对象
 * @see ContainerInfoDTO 容器信息数据传输对象
 */
@Service
public class ComposeProjectDiscovery {

    private static final Logger log = LoggerFactory.getLogger(ComposeProjectDiscovery.class);

    /** docker-compose 添加的项目名 label（用于按项目分组容器） */
    private static final String COMPOSE_PROJECT_LABEL = "com.docker.compose.project";
    /** docker-compose 添加的服务名 label（对应 docker-compose.yml 中的 services 键名） */
    private static final String COMPOSE_SERVICE_LABEL = "com.docker.compose.service";
    /** docker-compose 添加的工作目录 label（docker-compose.yml 文件所在目录） */
    private static final String COMPOSE_WORKING_DIR_LABEL = "com.docker.compose.project.working_dir";

    private final DockerClientService dockerClientService;

    public ComposeProjectDiscovery(DockerClientService dockerClientService) {
        this.dockerClientService = dockerClientService;
    }

    /**
     * 发现宿主机上所有 Docker Compose 项目。
     *
     * <p>扫描流程：</p>
     * <ol>
     *   <li>获取所有容器（含已停止的）</li>
     *   <li>过滤出带有 {@code com.docker.compose.project} label 的容器</li>
     *   <li>按项目名分组（groupingBy）</li>
     *   <li>为每组构建 {@link ComposeProjectDTO}（含服务列表、运行/停止统计等）</li>
     *   <li>按项目名称字母排序</li>
     * </ol>
     *
     * @return Compose 项目列表（按名称排序）
     */
    public List<ComposeProjectDTO> discoverProjects() {
        List<Container> containers = dockerClientService.listAllContainers();

        // 按项目名分组：只保留带有 compose project label 的容器
        Map<String, List<Container>> projectGroups = containers.stream()
                .filter(c -> c.getLabels() != null && c.getLabels().containsKey(COMPOSE_PROJECT_LABEL))
                .collect(Collectors.groupingBy(c -> c.getLabels().get(COMPOSE_PROJECT_LABEL)));

        return projectGroups.entrySet().stream()
                .map(entry -> buildProject(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ComposeProjectDTO::getProjectName))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定名称的 Compose 项目详情。
     *
     * @param projectName 项目名称（docker-compose 的项目名，通常是目录名）
     * @return 项目 DTO（如果存在），否则 empty
     */
    public Optional<ComposeProjectDTO> getProject(String projectName) {
        List<Container> containers = dockerClientService.listAllContainers();

        // 过滤出属于指定项目的所有容器
        List<Container> projectContainers = containers.stream()
                .filter(c -> c.getLabels() != null
                        && projectName.equals(c.getLabels().get(COMPOSE_PROJECT_LABEL)))
                .collect(Collectors.toList());

        if (projectContainers.isEmpty()) return Optional.empty();

        return Optional.of(buildProject(projectName, projectContainers));
    }

    /**
     * 从一组容器构建 Compose 项目 DTO。
     *
     * <p>统计运行中/已停止的服务数量，提取工作目录，
     * 按服务名字母排序后组装成完整的 ComposeProjectDTO。</p>
     *
     * @param projectName 项目名称
     * @param containers  属于该项目的容器列表
     * @return 构建完成的项目 DTO
     */
    private ComposeProjectDTO buildProject(String projectName, List<Container> containers) {
        // 将每个容器转换为服务信息 DTO，按服务名排序
        List<ContainerInfoDTO> services = containers.stream()
                .map(this::toContainerInfoDTO)
                .sorted(Comparator.comparing(ContainerInfoDTO::getServiceName))
                .collect(Collectors.toList());

        // 统计运行中和已停止的服务数
        int running = (int) services.stream()
                .filter(s -> s.getState() == ContainerState.RUNNING)
                .count();
        int stopped = services.size() - running;

        // 从第一个容器的 label 中提取工作目录
        String workingDir = containers.stream()
                .filter(c -> c.getLabels() != null && c.getLabels().containsKey(COMPOSE_WORKING_DIR_LABEL))
                .map(c -> c.getLabels().get(COMPOSE_WORKING_DIR_LABEL))
                .findFirst()
                .orElse("");

        return ComposeProjectDTO.builder()
                .projectName(projectName)
                .workingDir(workingDir)
                .totalServices(services.size())
                .runningServices(running)
                .stoppedServices(stopped)
                .services(services)
                .build();
    }

    /**
     * 将 Docker API 的 Container 对象转换为业务层 ContainerInfoDTO。
     *
     * <p>转换内容包括：</p>
     * <ul>
     *   <li>从 labels 提取 compose 服务名、项目名、工作目录</li>
     *   <li>格式化端口映射信息（"publicPort:privatePort/protocol"）</li>
     *   <li>通过 inspect 获取镜像 digest 信息（标识镜像唯一版本）</li>
     *   <li>转换容器状态枚举和时间戳</li>
     * </ul>
     *
     * @param container Docker API 返回的容器对象
     * @return 业务层容器信息 DTO
     */
    public ContainerInfoDTO toContainerInfoDTO(Container container) {
        Map<String, String> labels = container.getLabels() != null ? container.getLabels() : Map.of();

        // 从 compose label 提取服务名，无 label 时使用容器名作为回退
        String serviceName = labels.getOrDefault(COMPOSE_SERVICE_LABEL, getShortName(container));
        String projectName = labels.getOrDefault(COMPOSE_PROJECT_LABEL, "");
        String workingDir = labels.getOrDefault(COMPOSE_WORKING_DIR_LABEL, "");

        // 格式化端口映射：将 Docker API 的 Port 对象转为 "8080:80/tcp" 格式
        List<String> ports = container.getPorts() != null
                ? Arrays.stream(container.getPorts())
                        .map(p -> {
                            if (p.getPublicPort() != null && p.getPrivatePort() != null) {
                                return p.getPublicPort() + ":" + p.getPrivatePort() + "/" + (p.getType() != null ? p.getType() : "tcp");
                            }
                            return String.valueOf(p.getPrivatePort());
                        })
                        .collect(Collectors.toList())
                : List.of();

        // 获取镜像 digest 信息（用于标识镜像的唯一版本哈希）
        List<String> digests = new ArrayList<>();
        try {
            var inspect = dockerClientService.inspectContainer(container.getId());
            if (inspect.getImage() != null) {
                digests.add(inspect.getImage());  // 添加镜像 ID
            }
            // 尝试从镜像 inspect 获取 RepoDigests（Docker Hub 的 content-addressable 标识）
            try {
                var imageInspect = dockerClientService.getDockerClient()
                        .inspectImageCmd(container.getImage()).exec();
                if (imageInspect.getRepoDigests() != null) {
                    digests.addAll(imageInspect.getRepoDigests());
                }
            } catch (Exception e) {
                log.debug("Could not get image digests for {}: {}", container.getImage(), e.getMessage());
            }
        } catch (Exception e) {
            log.debug("Could not inspect container {}: {}", container.getId(), e.getMessage());
        }

        // Docker API 返回的创建时间是 Unix 时间戳（秒），转换为 LocalDateTime
        LocalDateTime createdAt = container.getCreated() != null
                ? LocalDateTime.ofInstant(Instant.ofEpochSecond(container.getCreated()), ZoneId.systemDefault())
                : null;

        return ContainerInfoDTO.builder()
                .containerId(container.getId())
                .containerName(getContainerName(container))
                .serviceName(serviceName)
                .projectName(projectName)
                .image(container.getImage())
                .imageId(container.getImageId())
                .state(ContainerState.fromDockerState(container.getState()))
                .status(container.getStatus())               // 人类可读的运行状态描述（如 "Up 2 hours"）
                .ports(ports)
                .digests(digests)
                .labels(labels)
                .createdAt(createdAt)
                .composeWorkingDir(workingDir)
                .build();
    }

    /**
     * 获取容器的可读名称。
     *
     * <p>Docker 容器名通常以 "/" 开头（如 "/my-app-web-1"），此方法去掉前缀。</p>
     *
     * @param container 容器对象
     * @return 去掉 "/" 前缀的容器名，无名称时返回 ID 前 12 位
     */
    private String getContainerName(Container container) {
        String[] names = container.getNames();
        if (names != null && names.length > 0) {
            String name = names[0];
            return name.startsWith("/") ? name.substring(1) : name;
        }
        return container.getId().substring(0, 12);
    }

    /**
     * 获取容器的短名称（当没有 compose service label 时使用）。
     *
     * @param container 容器对象
     * @return 容器名
     */
    private String getShortName(Container container) {
        return getContainerName(container);
    }
}
