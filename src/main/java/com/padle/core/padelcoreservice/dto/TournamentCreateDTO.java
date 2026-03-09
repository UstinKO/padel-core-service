package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class TournamentCreateDTO {

    @NotNull(message = "ID клуба обязателен")
    private Long clubId;

    @NotBlank(message = "Название турнира обязательно")
    @Size(min = 3, max = 255, message = "Название должно содержать от 3 до 255 символов")
    private String nombre;

    @NotNull(message = "Дата начала обязательна")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @FutureOrPresent(message = "Дата начала не может быть в прошлом")
    private LocalDate fechaInicio;

    @NotNull(message = "Время начала обязательно")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaInicio;

    @Size(max = 50, message = "Длительность не должна превышать 50 символов")
    private String duracion;

    @NotNull(message = "Формат турнира обязателен")
    private GenderFormat generoFormato;

    @NotBlank(message = "Категория/уровень обязательна")
    private String categoriaNivel;

    @NotNull(message = "Тип турнира обязателен")
    private TournamentType tipo;

    @NotNull(message = "Modalidad es obligatoria")
    private Modalidad modalidad;

    @NotNull(message = "Максимальное количество участников обязательно")
    @Min(value = 2, message = "Минимум 2 участника")
    @Max(value = 64, message = "Максимум 64 участника")
    private Integer cupoMax;

    @NotNull(message = "Цена обязательна")
    @DecimalMin(value = "0.0", inclusive = true, message = "Цена не может быть отрицательной")
    @Digits(integer = 8, fraction = 2, message = "Некорректный формат цены")
    private BigDecimal precio;

    private String moneda;

    @Future(message = "Дедлайн отмены должен быть в будущем")
    private LocalDateTime deadlineCancelacion;

    @Size(max = 1000, message = "Описание не должно превышать 1000 символов")
    private String infoDetallada;

    @NotBlank(message = "Контакт организатора обязателен")
    @Size(max = 100, message = "Контакт не должен превышать 100 символов")
    private String contactoOrganizador;

    @Pattern(regexp = "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})[/\\w .-]*/?$",
            message = "Некорректный URL")
    private String faqUrl;
}