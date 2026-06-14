package com.dockermanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 *
 * <p>包含三部分配置：</p>
 * <ol>
 *   <li><b>CORS 跨域</b>：允许开发模式下前端 dev server (localhost:5173) 访问后端 API</li>
 *   <li><b>SPA 路由转发</b>：将 Vue Router history 模式的前端路由转发到 index.html，
 *       确保直接访问 {@code /projects}、{@code /login} 等路径时能正确加载 SPA</li>
 *   <li><b>静态资源</b>：从 classpath:/static/ 提供前端构建产物</li>
 * </ol>
 *
 * <p>生产环境中前端文件通过 Maven 构建插件自动拷贝到
 * {@code src/main/resources/static/}，打包后位于 JAR 的 {@code /static/} 路径下。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置 CORS 跨域策略。
     *
     * <p>允许所有来源（开发模式下 Vite dev server 运行在不同端口）、
     * 所有标准 HTTP 方法和请求头的跨域请求，并允许携带 Cookie/Credential。</p>
     * <p>预检请求缓存 3600 秒（1 小时），减少 OPTIONS 请求次数。</p>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 配置 SPA 路由转发。
     *
     * <p>Vue Router 使用 history 模式，所有前端路由路径都需转发到 index.html，
     * 由 Vue Router 在客户端处理实际的路由匹配。</p>
     *
     * <p>注意：此配置不影响 {@code /api/**}、{@code /ws/**} 等后端路由，
     * 因为它们已在 SecurityConfig 中被独立处理。</p>
     *
     * @param registry 视图控制器注册器
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/login").setViewName("forward:/index.html");
        registry.addViewController("/projects").setViewName("forward:/index.html");
        registry.addViewController("/projects/{name}").setViewName("forward:/index.html");
        registry.addViewController("/containers/{id}").setViewName("forward:/index.html");
        registry.addViewController("/users").setViewName("forward:/index.html");
    }

    /**
     * 配置静态资源处理器。
     *
     * <p>将根路径 {@code /**} 映射到 classpath:/static/ 目录，
     * 用于提供前端构建产物（HTML/CSS/JS/图片等）。</p>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}
