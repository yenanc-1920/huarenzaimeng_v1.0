package com.huarenzaimeng.api.adminauth;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
final class AdminSpaController {
    @GetMapping("/admin/initialize") String initializationPage() {
        return "forward:/index.html";
    }
}
