package com.jobtrackr.jobtrackrbackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // loads application-test.yml (test-only JWT secret)
class JobtrackrBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
