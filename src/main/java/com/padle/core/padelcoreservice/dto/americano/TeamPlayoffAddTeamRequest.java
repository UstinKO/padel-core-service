package com.padle.core.padelcoreservice.dto.americano;

import lombok.Data;

/**
 * Запрос формы «Добавить команду» Team Playoff — каждый игрок задаётся либо
 * {@code id} (существующий игрок, найденный поиском), либо, если {@code id} не передан,
 * именем/фамилией/телефоном (новый игрок, с дедупом по телефону в
 * {@code PlayerService.resolverJugadorPorTelefonoOCrearInvitado}). Не заменяет
 * {@link TeamPlayoffTeamRequest}, который используется для редактирования уже
 * существующей команды.
 */
@Data
public class TeamPlayoffAddTeamRequest {
    private Long player1Id;
    private String player1Nombre;
    private String player1Apellido;
    private String player1Telefono;

    private Long player2Id;
    private String player2Nombre;
    private String player2Apellido;
    private String player2Telefono;

    private String registrationSource;
    private Boolean hasPaid;
    private Boolean attended;
    private String adminComment;
}
