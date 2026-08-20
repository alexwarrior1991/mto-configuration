package com.alejandro.mtoconfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(classes = MtoConfigurationApplicationTests.TestConfiguration.class)
class MtoConfigurationApplicationTests {

    @Test
    void contextLoads() {
    }

    @Configuration
    static class TestConfiguration {
    }

}
