package com.huarenzaimeng.api.adminauth;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
final class AdminSpaController {
    @GetMapping("/admin/login") String loginPage() {
        return "forward:/index.html";
    }

    @GetMapping("/admin/initialize") String initializationPage() {
        return "forward:/index.html";
    }

    @GetMapping("/admin/recover") String recoveryPage() {
        return "forward:/index.html";
    }
}
