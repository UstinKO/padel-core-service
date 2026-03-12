package com.padle.core.padelcoreservice.security.oauth2;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuth2UserManagementService userManagementService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.info("OAuth2 login attempt with provider: {}", registrationId);

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String email = extractEmail(attributes, registrationId);
        String name = extractName(attributes, registrationId);
        String firstName = extractFirstName(attributes, registrationId, name);
        String lastName = extractLastName(attributes, registrationId);

        PlayerPadel player = userManagementService.createOrUpdateUser(email, firstName, lastName, registrationId);

        return new CustomOAuth2User(oAuth2User, player);
    }

    private String extractEmail(Map<String, Object> attributes, String registrationId) {
        if ("google".equals(registrationId)) {
            return (String) attributes.get("email");
        } else if ("facebook".equals(registrationId)) {
            return (String) attributes.get("email");
        }
        return null;
    }

    private String extractName(Map<String, Object> attributes, String registrationId) {
        if ("google".equals(registrationId)) {
            return (String) attributes.get("name");
        } else if ("facebook".equals(registrationId)) {
            return (String) attributes.get("name");
        }
        return "Usuario";
    }

    private String extractFirstName(Map<String, Object> attributes, String registrationId, String fullName) {
        if ("google".equals(registrationId)) {
            String givenName = (String) attributes.get("given_name");
            return givenName != null ? givenName : fullName.split(" ")[0];
        } else if ("facebook".equals(registrationId)) {
            String firstName = (String) attributes.get("first_name");
            return firstName != null ? firstName : fullName.split(" ")[0];
        }
        return fullName.split(" ")[0];
    }

    private String extractLastName(Map<String, Object> attributes, String registrationId) {
        if ("google".equals(registrationId)) {
            String familyName = (String) attributes.get("family_name");
            return familyName != null ? familyName : "";
        } else if ("facebook".equals(registrationId)) {
            String lastName = (String) attributes.get("last_name");
            return lastName != null ? lastName : "";
        }
        return "";
    }
}