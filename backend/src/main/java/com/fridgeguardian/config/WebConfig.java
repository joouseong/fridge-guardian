package com.fridgeguardian.config;

import com.fridgeguardian.ingredient.image.LocalImageStorage;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final LocalImageStorage storage;

    public WebConfig(LocalImageStorage storage) { this.storage = storage; }

    /** 업로드한 식재료 사진을 /uploads/{uuid}.{jpg|png|webp}로 제공합니다. 파일 이름은 서버가 만든 UUID입니다. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 폴더가 아직 없으면 toUri()에 끝 슬래시가 붙지 않으므로 직접 보정합니다.
        String location = storage.directory().toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(location)
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable());
    }
}
