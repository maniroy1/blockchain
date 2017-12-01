package com.moglix.reports.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    
    @RequestMapping("/ping")
    public String ping() {
        return "Welcome to Moglix Report Card Module";
    }

}
