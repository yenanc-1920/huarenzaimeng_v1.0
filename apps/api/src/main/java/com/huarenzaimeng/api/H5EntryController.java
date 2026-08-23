package com.huarenzaimeng.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class H5EntryController {
    @GetMapping({"/h5", "/h5/"})
    String index() {
        return "forward:/h5/index.html";
    }
}
