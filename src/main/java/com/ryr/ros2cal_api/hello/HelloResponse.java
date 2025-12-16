package com.ryr.ros2cal_api.hello;

import java.time.Instant;
import java.util.UUID;

public record HelloResponse(UUID id, String message, Instant createdAt) {
}
