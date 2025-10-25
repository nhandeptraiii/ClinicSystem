package vn.project.ClinicSystem.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${clinicsystem.storage.avatars-dir:uploads/avatars}")
    private String avatarsDir;

    @Value("${clinicsystem.storage.avatars-public-prefix:/uploads/avatars}")
    private String avatarsPublicPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String cleanedPrefix = avatarsPublicPrefix.endsWith("/") ? avatarsPublicPrefix : avatarsPublicPrefix + "/";
        Path uploadPath = Paths.get(avatarsDir).toAbsolutePath().normalize();
        String location = uploadPath.toUri().toString();
        registry.addResourceHandler(cleanedPrefix + "**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
