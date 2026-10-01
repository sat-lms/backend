package com.sat.lms.global.config;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.embedded.TomcatWebServerFactoryCustomizer;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 프로파일(application-prod.yaml)의 forward-headers 설정으로 실제 Tomcat을 띄워,
 * 앞단 프록시(Caddy)가 넘긴 X-Forwarded-For가 remoteAddr에 반영되는지 검증한다(#130).
 * 요청 제한의 ClientIpResolver는 remoteAddr만 보므로, 여기서 remoteAddr가 바뀌면
 * 요청 제한도 클라이언트 IP 기준으로 동작한다. 테스트 요청은 127.0.0.1에서 오므로
 * Tomcat 기본 내부 프록시 대역에 속해 Caddy와 같은 신뢰 프록시로 취급된다.
 */
class ForwardedHeadersConfigTest {

    private WebServer webServer;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void startTomcatWithProdServerProperties() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yaml"))) {
            environment.getPropertySources().addFirst(source);
        }
        ServerProperties serverProperties = Binder.get(environment)
                .bindOrCreate("server", ServerProperties.class);

        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
        new TomcatWebServerFactoryCustomizer(environment, serverProperties).customize(factory);
        webServer = factory.getWebServer(servletContext ->
                servletContext.addServlet("remoteAddr", new RemoteAddrServlet()).addMapping("/"));
        webServer.start();
    }

    @AfterEach
    void stopTomcat() {
        webServer.stop();
    }

    @Test
    void remoteAddrIsClientIpForwardedByTrustedProxy() throws Exception {
        assertThat(remoteAddr("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void remoteAddrIsConnectionIpWithoutForwardedHeader() throws Exception {
        assertThat(remoteAddr(null)).isEqualTo("127.0.0.1");
    }

    @Test
    void spoofedLeftmostForwardedIpIsIgnored() throws Exception {
        // 클라이언트가 X-Forwarded-For를 위조해 앞에 붙여도, 신뢰 프록시가 마지막에 덧붙인
        // 실제 접속 IP(가장 오른쪽의 신뢰하지 않는 주소)를 클라이언트 IP로 쓴다.
        assertThat(remoteAddr("198.51.100.99, 203.0.113.7")).isEqualTo("203.0.113.7");
    }

    private String remoteAddr(String forwardedFor) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + webServer.getPort() + "/"));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static class RemoteAddrServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.getWriter().write(request.getRemoteAddr());
        }
    }
}
