package com.fridgeguardian.ingredient.image;

import com.fridgeguardian.common.ApiException;
import com.fridgeguardian.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LocalImageStorage implements ImageStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalImageStorage.class);
    // 우리가 만든 이름만 삭제 대상으로 인정해 경로 조작(../)을 원천 차단합니다.
    private static final Pattern STORED_NAME = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");
    private final Path directory;

    public LocalImageStorage(AppProperties props) {
        this.directory = Path.of(props.upload().dir()).toAbsolutePath().normalize();
    }

    public Path directory() { return directory; }

    @Override
    public String save(byte[] content, String extension) {
        String name = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(name), content);
            return name;
        } catch (IOException error) {
            log.error("이미지를 저장하지 못했습니다.", error);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_STORAGE_ERROR", "이미지를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
    }

    @Override
    public void delete(String fileName) {
        if (fileName == null || !STORED_NAME.matcher(fileName).matches()) return;
        try { Files.deleteIfExists(directory.resolve(fileName)); }
        catch (IOException error) { log.warn("이미지 파일을 삭제하지 못했습니다: {}", fileName); }
    }
}
