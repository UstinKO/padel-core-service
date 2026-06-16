package com.padle.core.padelcoreservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SoloDoubleRegistrationDto {

    @NotBlank(message = "El tipo de registro es obligatorio")
    @Pattern(regexp = "ADD_LATER|SEARCH", message = "Tipo debe ser ADD_LATER o SEARCH")
    private String soloType;

    private Boolean shareContacts = false;
}
