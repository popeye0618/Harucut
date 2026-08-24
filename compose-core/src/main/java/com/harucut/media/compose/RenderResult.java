package com.harucut.media.compose;

// 렌더 한 번의 산출물 두 벌 — 보관용 원본과 목록 그리드용 축소본.
// 썸네일을 렌더러가 같이 만드는 이유: 완성본을 다시 디코드해 줄이는 낭비를
// 캔버스가 아직 메모리에 있는 시점에 끝낸다.
//
// 이름이 비대칭인 것은 의도다 — full 의 포맷은 호출자가 정하지만(ImageFormat),
// 썸네일은 목록 그리드 전용이라 항상 JPEG 다. 필드 이름이 그 차이를 드러낸다.
// (원래는 fullPng 이었다. 타입에 포맷이 박혀 있어서 포맷을 고를 수 없었다)
public record RenderResult(byte[] full, byte[] thumbnailJpeg) {
}
