package com.padle.core.padelcoreservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "club_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, unique = true, length = 255)
    private String nombre;

    @Column(name = "direccion", length = 500)
    private String direccion;

    @Column(name = "zona_ciudad", length = 100)
    private String zonaCiudad;

    @Column(name = "telefono_contacto", length = 50)
    private String telefonoContacto;

    @Column(name = "email_contacto", length = 255)
    private String emailContacto;

    @Column(name = "mapa_url", length = 500)
    private String mapaUrl;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Вспомогательные методы
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