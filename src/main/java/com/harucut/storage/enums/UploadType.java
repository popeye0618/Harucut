package com.harucut.storage.enums;

public enum UploadType {
    PROFILE,
    FRAME,
    FRAME_COMPONENT,
    // 네컷 합성의 원본 사진 — 임시 자산(합성 성공 시 삭제). 완성본 업로드 타입은 의도적으로 없다:
    // 결과물은 사용자가 아니라 서버(Lambda)가 만들어 올린다 —
    // 사용자가 올릴 수 있게 하면 합성하지 않은 이미지를 결과물로 위장할 수 있다
    FOURCUT_SOURCE
}
