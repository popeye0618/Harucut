package com.harucut.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisplayNameUpdateRequest(
        @NotBlank @Size(max = 255) String displayName
) {
}
