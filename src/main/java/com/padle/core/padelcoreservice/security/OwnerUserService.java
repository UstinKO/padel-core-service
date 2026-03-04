package com.padle.core.padelcoreservice.security;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.repository.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnerUserService { // Убрали implements UserDetailsService

    private final OwnerRepository ownerRepository;

    @Transactional(readOnly = true)
    public Owner loadUserByUsername(String email) throws UsernameNotFoundException {
        Owner owner = ownerRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Owner not found with email: " + email));

        if (!owner.getIsActive()) {
            throw new UsernameNotFoundException("Owner account is not active: " + email);
        }

        return owner;
    }
}