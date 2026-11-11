package com.padle.core.padelcoreservice.security.oauth2;

import com.padle.core.padelcoreservice.security.TournamentLogin;
import com.padle.core.padelcoreservice.security.AccountDestination;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public final class TournamentOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private final HttpSessionOAuth2AuthorizationRequestRepository delegate =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null || "app".equals(request.getParameter("client"))) {
            delegate.saveAuthorizationRequest(authorizationRequest, request, response);
            return;
        }
        var storedRequest = TournamentLogin.requestedTournament(request)
                .map(id -> OAuth2AuthorizationRequest.from(authorizationRequest)
                        .attributes(attributes -> attributes.put(TournamentLogin.PARAMETER, id))
                        .build())
                .orElse(authorizationRequest);
        var destination = AccountDestination.requested(request);
        if (destination.isPresent()) {
            storedRequest = OAuth2AuthorizationRequest.from(storedRequest)
                    .attributes(attributes -> attributes.put(AccountDestination.PARAMETER, destination.orElseThrow())).build();
        }
        delegate.saveAuthorizationRequest(storedRequest, request, response);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                HttpServletResponse response) {
        var authorizationRequest = delegate.removeAuthorizationRequest(request, response);
        if (authorizationRequest != null) {
            request.setAttribute(AccountDestination.PARAMETER, authorizationRequest.getAttribute(AccountDestination.PARAMETER));
            // Только серверный атрибут запроса, для которого Spring подтвердил OAuth state.
            request.setAttribute(TournamentLogin.PARAMETER, authorizationRequest.getAttribute(TournamentLogin.PARAMETER));
        }
        return authorizationRequest;
    }
}
