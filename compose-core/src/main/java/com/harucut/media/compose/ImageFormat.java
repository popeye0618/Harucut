package com.harucut.media.compose;

// 결과물 저장 포맷. 확장자와 MIME 타입을 한곳에서 낸다.
//
// 왜 묶어 두는가 — 이 셋(실제 바이트·key 확장자·contentType)이 어긋나는 사고는
// 컴파일에도 테스트에도 안 잡히고 S3에 올라간 뒤에야 드러난다.
// 서버는 key를, Lambda는 contentType을 만들기 때문에 값이 나뉘어 있으면
// 두 배포물이 각자 다른 답을 낼 수 있다. 한 enum에서 나오면 그 창이 없다.
//
// 품질(q)은 여기 없다 — 그건 확장자·MIME와 달리 어긋나도 파일이 깨지지 않는
// 순수한 렌더링 결정이라 렌더러 상수로 둔다 (THUMBNAIL_JPEG_QUALITY와 같은 성격)
public enum ImageFormat {

    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg");

    private final String extension;
    private final String mimeType;

    ImageFormat(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    // 점 없이 돌려준다 — key 조립부가 "job-1." + ext 형태로 쓴다
    public String extension() {
        return extension;
    }

    public String mimeType() {
        return mimeType;
    }
}
