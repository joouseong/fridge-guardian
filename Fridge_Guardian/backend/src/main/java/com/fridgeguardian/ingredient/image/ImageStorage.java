package com.fridgeguardian.ingredient.image;

/** 이미지 저장소 추상화. 개발은 로컬 폴더, 운영(다중 서버)은 S3 같은 구현으로 바꿀 수 있습니다. */
public interface ImageStorage {
    /** 서버가 만든 파일 이름(UUID + 확장자)을 반환합니다. 원본 파일 이름은 사용하지 않습니다. */
    String save(byte[] content, String extension);

    /** 없는 파일이나 형식이 다른 이름은 조용히 무시합니다. 실패해도 사용자 요청을 막지 않습니다. */
    void delete(String fileName);
}
