package com.sat.lms.auth.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    void usesOnlyRemoteAddressAndIgnoresForwardingHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");
        request.addHeader("Forwarded", "for=203.0.113.30");
        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.10");
    }

    @Test
    void nullAndBlankAddressesShareSafeFallbackKey() {
        assertThat(resolver.normalize(null)).isEqualTo("unknown");
        assertThat(resolver.normalize("  ")).isEqualTo("unknown");
    }

    @Test
    void normalizesIpv4PortAndEquivalentIpv6Representations() {
        assertThat(resolver.normalize("192.0.2.10:43120")).isEqualTo("192.0.2.10");
        assertThat(resolver.normalize("[2001:db8::1]:43120"))
                .isEqualTo(resolver.normalize("2001:0db8:0:0:0:0:0:1"));
    }
}
