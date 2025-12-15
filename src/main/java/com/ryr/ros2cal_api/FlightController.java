package com.ryr.ros2cal_api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FlightController {

    @GetMapping("/flightz")
    public ResponseEntity<String> flight() {
        return ResponseEntity.ok("ok");
    }
}
