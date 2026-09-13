package com.motadev.clone_reddit;

import com.motadev.clone_reddit.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CloneRedditApplicationTests {

	@Test
	void contextLoads() {
	}

}