package io.github.balanced.controlplane;

import io.github.balanced.controlplane.entity.UpstreamEntity;
import io.github.balanced.controlplane.repository.UpstreamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UpstreamControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UpstreamRepository upstreamRepository;

    @BeforeEach
    void cleanDb() {
        upstreamRepository.deleteAll();
    }

    @Test
    void createAndListUpstreams() throws Exception {
        mvc.perform(post("/api/upstreams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"host":"10.0.0.1","port":9001,"weight":3}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.host").value("10.0.0.1"))
                .andExpect(jsonPath("$.weight").value(3))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(header().exists("X-Config-Version"));

        mvc.perform(get("/api/upstreams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void deleteUpstream() throws Exception {
        var entity = new UpstreamEntity();
        entity.setHost("10.0.0.2");
        entity.setPort(9002);
        entity.setWeight(1);
        entity = upstreamRepository.save(entity);

        mvc.perform(delete("/api/upstreams/" + entity.getId()))
                .andExpect(status().isNoContent());

        assertThat(upstreamRepository.findAll()).isEmpty();
    }

    @Test
    void deleteNonExistentReturns404() throws Exception {
        mvc.perform(delete("/api/upstreams/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationRejectsInvalidPort() throws Exception {
        mvc.perform(post("/api/upstreams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"host":"10.0.0.1","port":0,"weight":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void validationRejectsBlankHost() throws Exception {
        mvc.perform(post("/api/upstreams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"host":"","port":8080,"weight":1}
                                """))
                .andExpect(status().isBadRequest());
    }
}