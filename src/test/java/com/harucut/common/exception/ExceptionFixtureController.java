package com.harucut.common.exception;

import com.harucut.common.response.Response;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fixture")
class ExceptionFixtureController {

    @GetMapping("/ok")
    public Response<Void> ok() {
        return Response.ok();
    }

    @GetMapping("/business")
    public Response<Void> business() {
        throw new BusinessException(GlobalErrorCode.NOT_FOUND, "리소스 없음 — 로그에만 남아야 한다");
    }

    @PostMapping("/body")
    public Response<Void> body(@Valid @RequestBody SampleRequest request) {
        return Response.ok();
    }

    @GetMapping("/type-mismatch")
    public Response<Void> typeMismatch(@RequestParam int value) {
        return Response.ok();
    }

    @GetMapping("/required-param")
    public Response<Void> requiredParam(@RequestParam String name) {
        return Response.ok();
    }

    @GetMapping("/required-cookie")
    public Response<Void> requiredCookie(@CookieValue String token) {
        return Response.ok();
    }

    /**
     * @Validated 없음 → HandlerMethodValidationException 경로
     */
    @GetMapping("/param-validation")
    public Response<Void> paramValidation(@RequestParam @Min(1) int size) {
        return Response.ok();
    }

    @GetMapping("/boom")
    public Response<Void> boom() {
        throw new IllegalStateException("예상하지 못한 오류");
    }

    public record SampleRequest(
            @NotBlank String email,
            @Size(min = 8, max = 20) String password
    ) {
    }

}