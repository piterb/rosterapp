package com.ryr.ros2cal_api.hello;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class HelloController {

    private final HelloService helloService;

    @GetMapping("/hello")
    public ResponseEntity<HelloResponse> createHello(@RequestParam(name = "message") String message) {
        Hello saved = helloService.save(message);
        HelloResponse response = new HelloResponse(saved.getId(), saved.getMessage(), saved.getCreatedAt());
        return ResponseEntity.ok(response);
    }
}
