package com.titu.nexus_claims;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite::memory:", "spring.datasource.hikari.maximum-pool-size=1"})
class NexusClaimsApplicationTests {

	@Test
	void contextLoads() {
	}

}
