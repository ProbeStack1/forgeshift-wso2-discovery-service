package com.forgeshift.wso2discovery.filter;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.MigrationAuditEntry;
import com.forgeshift.wso2discovery.service.MigrationAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Captures every request to the discovery surface as a row in
 * {@code wso2_migration_audit_info}. Runs once per request, hands off to the
 * async {@link MigrationAuditService} so it never blocks the response.
 *
 * Paths covered: {@code /wso2/**}, {@code /discoveries/**},
 * {@code /internal/wso2/**}, {@code /profiles/**}, {@code /organizations/**},
 * {@code /audit/**}, {@code /relations/**}.
 *
 * Static/health paths ({@code /actuator/**}, {@code /swagger-ui/**},
 * {@code /v3/api-docs/**}) are skipped to keep the audit table signal-to-noise
 * high.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRequestFilter extends OncePerRequestFilter {

    private final MigrationAuditService auditService;
    private final DiscoveryProperties props;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isAuditEnabled()) return true;
        String path = request.getRequestURI();
        if (path == null) return true;
        // Strip context path; matches what controller path looks like
        String cp = request.getContextPath();
        if (cp != null && !cp.isEmpty() && path.startsWith(cp)) {
            path = path.substring(cp.length());
        }
        // Audit only the discovery surface
        return !(path.startsWith("/wso2/")
                || path.startsWith("/discoveries")
                || path.startsWith("/internal/wso2/")
                || path.startsWith("/profiles")
                || path.startsWith("/organizations")
                || path.startsWith("/audit")
                || path.startsWith("/relations"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String serviceTxn = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        long startNs = System.nanoTime();

        String companyName = headerOrParam(req, "X-Company-Name", "companyName");
        String wso2Tenant = headerOrParam(req, "X-Wso2-Tenant", "wso2Tenant");
        String userEmail = headerOrParam(req, "X-User-Email", "userEmail");
        String requestTxn = headerOrParam(req, "X-Request-Transaction-Id", "requestTransactionId");

        try {
            chain.doFilter(req, res);
        } finally {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            int status = res.getStatus();

            MigrationAuditEntry entry = MigrationAuditEntry.builder()
                    .serviceTransactionId(serviceTxn)
                    .requestTransactionId(requestTxn)
                    .companyName(companyName)
                    .wso2Tenant(wso2Tenant)
                    .userEmail(userEmail)
                    .requestSource(req.getHeader("X-Request-Source"))
                    .operation(deriveOperation(req.getMethod(), req.getRequestURI()))
                    .httpMethod(req.getMethod())
                    .httpPath(req.getRequestURI())
                    .remoteIp(req.getRemoteAddr())
                    .requestedAt(requestedAt)
                    .completedAt(Instant.now())
                    .elapsedMs(elapsedMs)
                    .statusCode(status)
                    .status(status >= 200 && status < 400 ? "SUCCESS" : "FAILED")
                    .build();

            auditService.record(entry);
        }
    }

    private static String headerOrParam(HttpServletRequest req, String header, String param) {
        String v = req.getHeader(header);
        if (v != null && !v.isBlank()) return v;
        return req.getParameter(param);
    }

    private static String deriveOperation(String method, String uri) {
        if (uri == null) return method;
        // Strip the context path prefix
        String[] segs = uri.split("/");
        // Find the discovery surface root
        for (int i = 0; i < segs.length; i++) {
            String s = segs[i];
            if (s.isEmpty()) continue;
            if (s.equals("wso2") && i + 1 < segs.length) {
                return method + "_WSO2_" + segs[i + 1].toUpperCase();
            }
            if (s.equals("discoveries")) return method + "_DISCOVERIES";
            if (s.equals("profiles")) return method + "_PROFILES";
            if (s.equals("organizations")) return method + "_ORGANIZATIONS";
            if (s.equals("audit")) return method + "_AUDIT";
            if (s.equals("relations")) return method + "_RELATIONS";
        }
        return method + "_OTHER";
    }
}
