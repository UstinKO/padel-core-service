package com.padle.core.persistence.identity;

import com.padle.core.application.identity.PasswordResetTokens;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PasswordResetTokenMapper {
    PasswordResetTokens.Token toToken(PasswordResetToken row);
}
