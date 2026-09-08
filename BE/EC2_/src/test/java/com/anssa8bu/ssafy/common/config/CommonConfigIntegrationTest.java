package com.anssa8bu.ssafy.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CommonConfigIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void jacksonUsesIsoInstantAndRejectsLooseInput() throws Exception {
        String json = objectMapper.writeValueAsString(
                new JsonSample(Instant.parse("2026-09-07T05:00:00Z"), 1)
        );

        assertThat(json).contains("\"occurredAt\":\"2026-09-07T05:00:00Z\"");
        assertThatThrownBy(() -> objectMapper.readValue(
                "{\"occurredAt\":\"2026-09-07T05:00:00Z\",\"count\":1,\"unknown\":true}",
                JsonSample.class
        )).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> objectMapper.readValue(
                "{\"occurredAt\":\"2026-09-07T05:00:00Z\",\"count\":1.5}",
                JsonSample.class
        )).isInstanceOf(JacksonException.class);
    }

    @Test
    void redisStoresJsonWithStringKeyAndExpires() throws InterruptedException {
        String key = "test:common-config:" + UUID.randomUUID();
        try {
            redisTemplate.opsForValue().set(key, "value", Duration.ofMillis(300));

            assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("value");
            assertThat(redisTemplate.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);

            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (Boolean.TRUE.equals(redisTemplate.hasKey(key)) && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertThat(redisTemplate.hasKey(key)).isFalse();
        } finally {
            redisTemplate.delete(key);
        }
    }

    @Test
    void securityIsStatelessFailClosedAndProvidesPasswordHashing() throws Exception {
        String encoded = passwordEncoder.encode("test-password");
        assertThat(passwordEncoder.matches("test-password", encoded)).isTrue();
        assertThat(encoded).doesNotContain("test-password");

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/protected-test"))
                .andExpect(status().isForbidden());
    }

    private record JsonSample(Instant occurredAt, int count) {
    }
}
