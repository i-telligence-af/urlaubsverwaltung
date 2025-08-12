package org.synyx.urlaubsverwaltung.custom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.HashMap;

@Configuration
public class CustomConfig {

    private static final String CLIENT_ID = "itelligence"; // your registration id

    private final ItelligenceAuthSettings itlligenceAuthSettings;

    @Autowired
    public CustomConfig(ItelligenceAuthSettings itlligenceAuthSettings) {
        this.itlligenceAuthSettings = itlligenceAuthSettings;
    }


    @Bean
    ClientRegistrationRepository clientRegistrationRepository(OAuth2ClientProperties props) {
        var r = props.getRegistration().get(CLIENT_ID);           // your registration id
        var p = props.getProvider().get(r.getProvider());

        var metadata = new HashMap<String, Object>();
        metadata.put("end_session_endpoint", itlligenceAuthSettings.getEndSessionUri());

        var reg = ClientRegistration.withRegistrationId(CLIENT_ID)
            .clientId(r.getClientId())
            .clientSecret(r.getClientSecret())
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(r.getRedirectUri())
            .scope(r.getScope())
            .authorizationUri(p.getAuthorizationUri())
            .tokenUri(p.getTokenUri())
            .userInfoUri(p.getUserInfoUri())
            .userNameAttributeName(p.getUserNameAttribute())
            .jwkSetUri(p.getJwkSetUri())
            .providerConfigurationMetadata(metadata) // <-- inject end_session_endpoint
            .build();

        return new InMemoryClientRegistrationRepository(reg);
    }

}
