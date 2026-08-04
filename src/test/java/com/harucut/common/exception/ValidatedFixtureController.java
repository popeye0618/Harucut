package com.harucut.common.exception;

import com.harucut.common.response.Response;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fixture-validated")
@Validated
public class ValidatedFixtureController {

    @GetMapping("/param-validation")
    public Response<Void> paramValidation(@RequestParam @Min(1) int size) {
        return Response.ok();
    }
}
