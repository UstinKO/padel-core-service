package com.padle.core.padelcoreservice.security;

import com.padle.core.application.identity.OwnerAccounts;
import com.padle.core.padelcoreservice.mapper.OwnerPrincipalMapper;
import com.padle.core.padelcoreservice.model.Owner;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnerUserService {

    private final OwnerAccounts owners;
    private final OwnerPrincipalMapper principals;

    @Transactional(readOnly = true)
    public Owner loadUserByUsername(String email) throws UsernameNotFoundException {
        var login = owners.findForLogin(email)
                .orElseThrow(() -> new UsernameNotFoundException("Owner not found with email: " + email));

        if (!login.account().isActive()) {
            throw new UsernameNotFoundException("Owner account is not active: " + email);
        }

        Owner principal = principals.toPrincipal(login.account());
        principal.setPassword(login.passwordHash());
        return principal;
    }
}