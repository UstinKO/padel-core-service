package com.padle.core.application.clubs;

/** Изменения принадлежат транзакции вызывающего сценария. */
public interface ClubAdminWritePort {
    ClubProfile create(ClubProfile.Fields fields, Long createdBy);

    ClubProfile update(Long id, ClubProfile.Fields fields);

    void setActive(Long id, boolean active);

    void delete(Long id);
}
