package com.motadev.clone_reddit.security;

import com.motadev.clone_reddit.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ActuatorHealthGroupsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HealthEndpointGroups healthEndpointGroups;

    @Test
    void livenessGroupContainsOnlyLivenessState() {
        var liveness = healthEndpointGroups.get("liveness");
        assertThat(liveness).isNotNull();
        assertThat(liveness.isMember("livenessState")).isTrue();
        assertThat(liveness.isMember("db")).isFalse();
        assertThat(liveness.isMember("rabbit")).isFalse();
        assertThat(liveness.isMember("mail")).isFalse();
    }

    @Test
    void readinessGroupContainsReadinessStateAndDbOnly() {
        var readiness = healthEndpointGroups.get("readiness");
        assertThat(readiness).isNotNull();
        assertThat(readiness.isMember("readinessState")).isTrue();
        assertThat(readiness.isMember("db")).isTrue();
        assertThat(readiness.isMember("rabbit")).isFalse();
        assertThat(readiness.isMember("mail")).isFalse();
    }

    @Test
    void readinessProbeStaysUpWhenOnlyTrafficDependenciesMatter() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessProbeStaysUpIndependentlyOfDependencyGroups() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
