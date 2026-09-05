package com.sat.lms.auth.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        return normalize(request.getRemoteAddr());
    }

    String normalize(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return "unknown";
        }
        String address = remoteAddress.trim();
        if (address.startsWith("[") && address.contains("]")) {
            address = address.substring(1, address.indexOf(']'));
        } else if (address.indexOf(':') == address.lastIndexOf(':') && address.contains(":")) {
            String candidate = address.substring(0, address.indexOf(':'));
            if (candidate.matches("(?:\\d{1,3}\\.){3}\\d{1,3}")) {
                address = candidate;
            }
        }
        int zoneIndex = address.indexOf('%');
        if (zoneIndex >= 0) {
            address = address.substring(0, zoneIndex);
        }
        if (!address.matches("[0-9A-Fa-f:.]+")) {
            return address.toLowerCase(Locale.ROOT);
        }
        try {
            return InetAddress.getByName(address).getHostAddress().toLowerCase(Locale.ROOT);
        } catch (UnknownHostException ignored) {
            return address.toLowerCase(Locale.ROOT);
        }
    }
}
