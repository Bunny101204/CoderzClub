package com.coderzclub.service;

import com.coderzclub.model.OrganizationDomain;
import com.coderzclub.model.OrganizationDomainStatus;
import com.coderzclub.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Application-side custom domain registry and local verification hooks.
 * Does not provision DNS or TLS certificates.
 *
 * <p>Public custom-domain tenant resolution stays disabled until
 * {@code app.custom-domains.public-resolution-enabled=true} after DNS TXT ownership,
 * Cloudflare/edge routing, and TLS are verified out-of-band.
 */
@Service
public class OrganizationDomainService {
    private static final Pattern HOSTNAME = Pattern.compile(
        "^(?=.{1,253}$)(?!-)[a-z0-9-]+(\\.[a-z0-9-]+)+$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final long VERIFY_TTL_MS = 7L * 24 * 60 * 60 * 1000;
    private final MongoTemplate mongoTemplate;
    private final OrganizationAccessService access;
    private final boolean publicResolutionEnabled;
    private final SecureRandom random = new SecureRandom();

    public OrganizationDomainService(
        MongoTemplate mongoTemplate,
        OrganizationAccessService access,
        @Value("${app.custom-domains.public-resolution-enabled:false}") boolean publicResolutionEnabled
    ) {
        this.mongoTemplate = mongoTemplate;
        this.access = access;
        this.publicResolutionEnabled = publicResolutionEnabled;
    }

    public Map<String, Object> register(Authentication authentication, String organizationId, String rawHostname) {
        access.requireOrgAdministrator(authentication, organizationId);
        User actor = access.requireUser(authentication);
        String hostname = normalizeHostname(rawHostname);
        rejectReserved(hostname);
        if (mongoTemplate.exists(Query.query(Criteria.where("hostname").is(hostname)), OrganizationDomain.class)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hostname is already registered");
        }
        OrganizationDomain domain = new OrganizationDomain();
        domain.setOrganizationId(organizationId);
        domain.setHostname(hostname);
        domain.setStatus(OrganizationDomainStatus.PENDING.name());
        domain.setVerificationToken(randomToken());
        domain.setVerificationExpiresAt(new Date(System.currentTimeMillis() + VERIFY_TTL_MS));
        domain.setCreatedAt(new Date());
        domain.setUpdatedAt(new Date());
        domain.setCreatedBy(actor.getId());
        return toMap(mongoTemplate.save(domain));
    }

    public List<Map<String, Object>> list(Authentication authentication, String organizationId) {
        access.requireOrgAdministrator(authentication, organizationId);
        return mongoTemplate.find(
                Query.query(Criteria.where("organizationId").is(organizationId)),
                OrganizationDomain.class)
            .stream()
            .map(this::toMap)
            .toList();
    }

    /**
     * Local/synthetic verification: caller proves possession of the issued token
     * (simulates DNS TXT check in automated tests). Production must verify DNS TXT.
     */
    public Map<String, Object> markVerifiedLocally(
        Authentication authentication,
        String organizationId,
        String domainId,
        String presentedToken
    ) {
        access.requireOrgAdministrator(authentication, organizationId);
        OrganizationDomain domain = requireOwned(organizationId, domainId);
        if (!OrganizationDomainStatus.PENDING.name().equals(domain.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Domain is not pending verification");
        }
        if (domain.getVerificationExpiresAt() != null
            && domain.getVerificationExpiresAt().before(new Date())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification token expired");
        }
        if (!StringUtils.hasText(presentedToken)
            || !presentedToken.equals(domain.getVerificationToken())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification token");
        }
        domain.setStatus(OrganizationDomainStatus.VERIFIED.name());
        domain.setVerifiedAt(new Date());
        domain.setUpdatedAt(new Date());
        return toMap(mongoTemplate.save(domain));
    }

    public Map<String, Object> activate(Authentication authentication, String organizationId, String domainId) {
        access.requireOrgAdministrator(authentication, organizationId);
        OrganizationDomain domain = requireOwned(organizationId, domainId);
        OrganizationDomainStatus status = OrganizationDomainStatus.from(domain.getStatus());
        if (status != OrganizationDomainStatus.VERIFIED && status != OrganizationDomainStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Domain must be verified before activation");
        }
        // Activation records intent in the registry. Public Host-based tenant routing still
        // requires app.custom-domains.public-resolution-enabled after edge/TLS validation.
        domain.setStatus(OrganizationDomainStatus.ACTIVE.name());
        domain.setUpdatedAt(new Date());
        return toMap(mongoTemplate.save(domain));
    }

    public Map<String, Object> disable(Authentication authentication, String organizationId, String domainId) {
        access.requireOrgAdministrator(authentication, organizationId);
        OrganizationDomain domain = requireOwned(organizationId, domainId);
        domain.setStatus(OrganizationDomainStatus.DISABLED.name());
        domain.setUpdatedAt(new Date());
        return toMap(mongoTemplate.save(domain));
    }

    /**
     * Resolve a tenant organization id from a trusted hostname.
     * Unknown / pending / verified / disabled hosts return null (public CoderzClub experience).
     * Never trusts X-Forwarded-Host alone — caller must pass a host from
     * {@link com.coderzclub.config.TrustedRequestHostResolver}.
     */
    public String resolveOrganizationIdForHostname(String rawHostname) {
        if (!publicResolutionEnabled) {
            return null;
        }
        String hostname = normalizeHostnameQuiet(rawHostname);
        if (hostname == null) {
            return null;
        }
        OrganizationDomain domain = mongoTemplate.findOne(
            Query.query(Criteria.where("hostname").is(hostname)),
            OrganizationDomain.class
        );
        if (domain == null) {
            return null;
        }
        if (!OrganizationDomainStatus.from(domain.getStatus()).mayResolveTenant()) {
            return null;
        }
        // Binding is exact-hostname only; sibling or parent hosts never inherit tenancy.
        if (!hostname.equals(domain.getHostname())) {
            return null;
        }
        return domain.getOrganizationId();
    }

    public boolean isPublicResolutionEnabled() {
        return publicResolutionEnabled;
    }

    public static String normalizeHostname(String raw) {
        String hostname = normalizeHostnameQuiet(raw);
        if (hostname == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid hostname");
        }
        return hostname;
    }

    private static String normalizeHostnameQuiet(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String host = raw.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("http://") || host.startsWith("https://")) {
            try {
                host = java.net.URI.create(host).getHost();
            } catch (Exception ex) {
                return null;
            }
        }
        if (host == null) {
            return null;
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        int colon = host.indexOf(':');
        if (colon > 0) {
            host = host.substring(0, colon);
        }
        if (IPV4_LITERAL.matcher(host).matches()) {
            return null;
        }
        if (!HOSTNAME.matcher(host).matches()) {
            return null;
        }
        return host;
    }

    private void rejectReserved(String hostname) {
        if ("localhost".equals(hostname)
            || hostname.endsWith(".localhost")
            || hostname.endsWith(".local")
            || hostname.endsWith(".internal")
            || "coderzclub.site".equals(hostname)
            || hostname.endsWith(".coderzclub.site")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hostname is reserved");
        }
        // Local registry tests may use *.example.test / *.coderzclub.test only.
        if (hostname.endsWith(".example.test") || hostname.endsWith(".coderzclub.test")) {
            return;
        }
        if (hostname.endsWith(".test")
            || hostname.endsWith(".invalid")
            || hostname.endsWith(".example")
            || hostname.endsWith(".localhost")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hostname is reserved");
        }
    }

    private OrganizationDomain requireOwned(String organizationId, String domainId) {
        OrganizationDomain domain = mongoTemplate.findById(domainId, OrganizationDomain.class);
        if (domain == null || !organizationId.equals(domain.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Domain not found");
        }
        return domain;
    }

    private String randomToken() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private Map<String, Object> toMap(OrganizationDomain domain) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", domain.getId());
        map.put("organizationId", domain.getOrganizationId());
        map.put("hostname", domain.getHostname());
        map.put("status", domain.getStatus());
        map.put("verificationToken", domain.getVerificationToken());
        map.put("dnsTxtName", "_coderzclub-verify." + domain.getHostname());
        map.put("dnsTxtValue", domain.getVerificationToken());
        map.put("verificationExpiresAt", domain.getVerificationExpiresAt());
        map.put("verifiedAt", domain.getVerifiedAt());
        map.put("createdAt", domain.getCreatedAt());
        map.put("updatedAt", domain.getUpdatedAt());
        return map;
    }

}
