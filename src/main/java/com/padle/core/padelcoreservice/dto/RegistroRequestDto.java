package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.validation.ValidDomain;
import com.padle.core.padelcoreservice.validation.NoBotPattern;
import dev.caceresenzo.disposableemaildomains.validation.constraints.NonDisposableEmailDomain;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistroRequestDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    @Pattern(regexp = "^[\\p{L} .'-]+$", message = "El nombre contiene caracteres inválidos")
    @NoBotPattern(message = "Nombre parece ser generado automáticamente")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(min = 2, max = 100, message = "El apellido debe tener entre 2 y 100 caracteres")
    @Pattern(regexp = "^[\\p{L} .'-]+$", message = "El apellido contiene caracteres inválidos")
    @NoBotPattern(message = "Apellido parece ser generado automáticamente")
    private String apellido;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    @Size(max = 150, message = "El email no puede tener más de 150 caracteres")
    @ValidDomain(message = "Dominio de email no válido o sospechoso")
    @NonDisposableEmailDomain
    private String email;

    @Size(max = 20, message = "El teléfono no puede tener más de 20 caracteres")
    @Pattern(regexp = "^[+]?[0-9\\s-]{8,20}$", message = "Formato de teléfono inválido")
    private String telefono;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$",
            message = "La contraseña debe contener al menos un número, una mayúscula, una minúscula y un carácter especial")
    private String password;

    @NotBlank(message = "La confirmación de contraseña es obligatoria")
    private String confirmPassword;

    public boolean passwordsMatch() {
        return password != null && confirmPassword != null && password.equals(confirmPassword);
    }
}