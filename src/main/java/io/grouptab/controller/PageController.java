package io.grouptab.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Serves join.html for any /join/{token} path
// The actual joining logic is done via the API — this just serves the HTML page
@Controller
public class PageController {

    @GetMapping("/join/{token}")
    public String joinPage() {
        return "forward:/join.html";
    }
}