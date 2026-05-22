package com.mikemes.common.security.auth;

import com.mikemes.common.security.privilege.PrivilegeCache;
import com.mikemes.common.security.privilege.PrivilegeGrantedAuthority;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class MikeMESJwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {

    private final PrivilegeCache privilegeCache;

    public MikeMESJwtAuthenticationConverter(PrivilegeCache privilegeCache) {
        this.privilegeCache = privilegeCache;
    }

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        var extractor = new JwtClaimsExtractor(jwt);

        try {
            String orgId = extractor.getOrgId();
            OrganisationContextHolder.setOrgId(orgId);
        } catch (MissingClaimException e) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_claim", e.getMessage(), null));
        }

        Collection<GrantedAuthority> authorities = resolveAuthorities(extractor);
        return new JwtAuthenticationToken(jwt, authorities);
    }

    private Set<GrantedAuthority> resolveAuthorities(JwtClaimsExtractor extractor) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String role : extractor.getRoles()) {
            for (String privilege : privilegeCache.getPrivilegesForRole(role)) {
                authorities.add(new PrivilegeGrantedAuthority(privilege));
            }
        }
        return authorities;
    }
}
