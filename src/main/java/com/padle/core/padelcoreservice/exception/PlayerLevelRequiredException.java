package com.padle.core.padelcoreservice.exception;

// LFPT-0303: подкласс, а не отдельный тип ошибки — чтобы существующий код, перехватывающий
// TournamentRegistrationException, продолжал работать без изменений; контроллеры, которым
// нужно отличить этот случай (для фронтенд-попапа "Указать уровень"), проверяют instanceof
// до общего catch.
public class PlayerLevelRequiredException extends TournamentRegistrationException {
    public PlayerLevelRequiredException(String message) {
        super(message);
    }
}
