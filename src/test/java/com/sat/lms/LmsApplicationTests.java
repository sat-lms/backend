package com.sat.lms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sat.lms.admin.service.MemberReviewService;
import com.sat.lms.assignment.repository.AssignmentRepository;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.AssignmentAttachmentRepository;
import com.sat.lms.attachment.repository.NoticeAttachmentRepository;
import com.sat.lms.attachment.repository.SubmissionAttachmentRepository;
import com.sat.lms.auth.service.AuthService;
import com.sat.lms.global.exception.GlobalExceptionHandler;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.member.repository.MemberReviewRepository;
import com.sat.lms.notice.repository.NoticeReadRepository;
import com.sat.lms.notice.repository.NoticeRepository;
import com.sat.lms.submission.repository.SubmissionRepository;
import com.sat.lms.global.config.AwsProperties;
import com.sat.lms.global.storage.FileStorage;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
class LmsApplicationTests {

	@Autowired ApplicationContext applicationContext;
	@MockitoBean MemberRepository memberRepository;
	@MockitoBean MemberReviewRepository memberReviewRepository;
	@MockitoBean NoticeRepository noticeRepository;
	@MockitoBean NoticeReadRepository noticeReadRepository;
	@MockitoBean AssignmentRepository assignmentRepository;
	@MockitoBean AttachmentRepository attachmentRepository;
	@MockitoBean AssignmentAttachmentRepository assignmentAttachmentRepository;
	@MockitoBean NoticeAttachmentRepository noticeAttachmentRepository;
	@MockitoBean SubmissionAttachmentRepository submissionAttachmentRepository;
	@MockitoBean SubmissionRepository submissionRepository;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;
	@Autowired S3Client s3Client;
	@Autowired S3Presigner s3Presigner;
	@Autowired AwsProperties awsProperties;
	@Autowired FileStorage fileStorage;
	@Autowired MultipartProperties multipartProperties;

	@Test
	void contextLoads() {
		assertThat(applicationContext.getBean(AuthService.class)).isNotNull();
		assertThat(applicationContext.getBean(MemberReviewService.class)).isNotNull();
		assertThat(applicationContext.getBean(GlobalExceptionHandler.class)).isNotNull();
		assertThat(applicationContext.getBean(SecurityFilterChain.class)).isNotNull();
		assertThat(s3Client).isNotNull();
		assertThat(s3Presigner).isNotNull();
		assertThat(awsProperties.getRegion()).isEqualTo("ap-northeast-2");
		assertThat(fileStorage).isNotNull();
		assertThat(multipartProperties.getMaxFileSize().toMegabytes()).isEqualTo(50L);
		assertThat(multipartProperties.getMaxRequestSize().toMegabytes()).isEqualTo(100L);
	}

}
