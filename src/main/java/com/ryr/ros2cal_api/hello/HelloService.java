package com.ryr.ros2cal_api.hello;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HelloService {

    private final HelloRepository repository;

    @Transactional
    public Hello save(String message) {
        Hello entity = Hello.builder()
                .message(message)
                .build();
        return repository.save(entity);
    }
}
