package com.fridgeguardian.ingredient.image;

import com.fridgeguardian.common.ApiException;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 5MB 이하의 JPEG/PNG/WEBP만 받습니다. 확장자·Content-Type이 아니라 파일 앞부분(매직 바이트)으로 판별합니다. */
@Component
public class ImageValidator {
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    public record Validated(byte[] content, String extension) { }

    public Validated validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("이미지 파일을 선택해 주세요.");
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        byte[] content;
        try { content = file.getBytes(); }
        catch (IOException error) { throw invalid("이미지를 읽지 못했어요. 다른 파일로 다시 시도해 주세요."); }
        if (content.length > MAX_BYTES) throw tooLarge();
        String extension = detect(content);
        if (extension == null) throw invalid("JPG, PNG, WEBP 이미지만 올릴 수 있어요.");
        return new Validated(content, extension);
    }

    public Validated validate(byte[] content) {
        if (content == null || content.length == 0) throw invalid("이미지 파일이 비어 있어요.");
        if (content.length > MAX_BYTES) throw tooLarge();
        String extension = detect(content);
        if (extension == null) throw invalid("JPG, PNG, WEBP 이미지만 저장할 수 있어요.");
        return new Validated(content, extension);
    }

    static String detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
            && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return "png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return null;
    }

    public static ApiException tooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "이미지는 5MB 이하만 올릴 수 있어요.");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", message);
    }
}
