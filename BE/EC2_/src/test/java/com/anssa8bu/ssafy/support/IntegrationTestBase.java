package com.anssa8bu.ssafy.support;

import com.anssa8bu.ssafy.support.container.SharedTestContainers;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Spring 컨텍스트가 필요한 모든 통합 테스트의 베이스.
 *
 * <p>의도적으로 {@code @Transactional}을 붙이지 않는다. 커밋·롤백·동시성을 검증하는 테스트가 자동 롤백 트랜잭션에 감싸이지 않도록, 필요한 테스트
 * 클래스에서 직접 지정한다. 하위 클래스가 {@code @SpringBootTest(properties=...)}를 다시 선언해도 컨테이너 속성 주입은 그대로 상속된다.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

  @DynamicPropertySource
  static void containerProperties(DynamicPropertyRegistry registry) {
    SharedTestContainers.register(registry);
  }
}
