package com.ryr.ros2cal_api.hello;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HelloRepository extends JpaRepository<Hello, UUID> {
}
