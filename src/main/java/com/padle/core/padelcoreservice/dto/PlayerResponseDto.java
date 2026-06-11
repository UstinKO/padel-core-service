package com.padle.core.padelcoreservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerResponseDto {

    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private String telegramUsername;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm:ss")
    private LocalDateTime fechaRegistro;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm:ss")
    private LocalDateTime fechaActualizacion;

    private boolean emailConfirmado;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm:ss")
    private LocalDateTime fechaConfirmacionEmail;

    private boolean activo;

    private String nombreCompleto;

    private Nivel nivelJugador;
}