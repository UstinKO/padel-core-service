package com.padle.core.padelcoreservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubDto {
    private Long id;
    private String nombre;
    private String direccion;
    private String zonaCiudad;
    private String telefonoContacto;
    private String emailContacto;
    private String mapaUrl;
    private String websiteUrl;
    private String descripcion;
    private String logoUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Boolean isActive;

    // Computed fields
    public String getDireccionCompleta() {
        if (direccion == null && zonaCiudad == null) {
            return null;
        }
        if (direccion == null) {
            return zonaCiudad;
        }
        if (zonaCiudad == null) {
            return direccion;
        }
        return direccion + ", " + zonaCiudad;
    }
}