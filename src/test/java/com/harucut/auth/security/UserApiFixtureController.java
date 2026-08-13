package com.harucut.auth.security;

import com.harucut.common.response.Response;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fixture/user")
@PreAuthorize("hasRole('USER')")
class UserApiFixtureController {

    @GetMapping
    public Response<Void> get() {
        return Response.ok();
    }
}
