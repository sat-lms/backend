package com.sat.lms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sat.lms.admin.service.MemberReviewService;
import com.sat.lms.auth.service.AuthService;
import com.sat.lms.global.exception.GlobalExceptionHandler;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.member.repository.MemberReviewRepository;
import com.sat.lms.notice.repository.NoticeReadRepository;
import com.sat.lms.notice.repository.NoticeRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
		"jwt.secret=test-secret-key-must-be-at-least-32-bytes"
})
class LmsApplicationTests {

	@Autowired ApplicationContext applicationContext;
	@MockitoBean MemberRepository memberRepository;
	@MockitoBean MemberReviewRepository memberReviewRepository;
	@MockitoBean NoticeRepository noticeRepository;
	@MockitoBean NoticeReadRepository noticeReadRepository;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	void contextLoads() {
		assertThat(applicationContext.getBean(AuthService.class)).isNotNull();
		assertThat(applicationContext.getBean(MemberReviewService.class)).isNotNull();
		assertThat(applicationContext.getBean(GlobalExceptionHandler.class)).isNotNull();
		assertThat(applicationContext.getBean(SecurityFilterChain.class)).isNotNull();
	}

}
