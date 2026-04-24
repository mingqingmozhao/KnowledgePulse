package com.ahy.knowledgepulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${knowledgepulse.avatar-storage-dir:storage/avatars}")
    private String avatarStorageDir;

    @Value("${knowledgepulse.attachment-storage-dir:storage/attachments}")
    private String attachmentStorageDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        addStorageResourceHandler(registry, "/media/avatars/**", avatarStorageDir);
        addStorageResourceHandler(registry, "/media/attachments/**", attachmentStorageDir);
    }

    private void addStorageResourceHandler(ResourceHandlerRegistry registry, String pattern, String storageDirectory) {
        Path storagePath = Paths.get(storageDirectory).toAbsolutePath().normalize();
        String resourceLocation = storagePath.toUri().toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation = resourceLocation + "/";
        }
        registry.addResourceHandler(pattern)
                .addResourceLocations(resourceLocation);
    }
}
