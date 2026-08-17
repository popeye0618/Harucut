package com.harucut.media.compose;

// 렌더 한 번의 산출물 두 벌 — 보관용 원본 PNG와 목록 그리드용 축소본 JPEG.
// 썸네일을 렌더러가 같이 만드는 이유: 완성 PNG를 다시 디코드해 줄이는 낭비를
// 캔버스가 아직 메모리에 있는 시점에 끝낸다
public record RenderResult(byte[] fullPng, byte[] thumbnailJpeg) {
}
