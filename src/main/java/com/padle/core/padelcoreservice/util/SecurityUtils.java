package com.padle.core.padelcoreservice.util;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.security.oauth2.CustomOAuth2User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;

@Slf4j
public class SecurityUtils {

    /**
     * Извлекает PlayerPadel из principal объекта Spring Security
     * @param principal объект principal (может быть разных типов)
     * @return PlayerPadel или null если не удалось извлечь
     */
    public static PlayerPadel extractPlayer(Object principal) {
        if (principal == null) {
            return null;
        }

        if (principal instanceof CustomOAuth2User) {
            return ((CustomOAuth2User) principal).getPlayer();
        } else if (principal instanceof PlayerPadel) {
            return (PlayerPadel) principal;
        } else if (principal instanceof UserDetails) {
            // Для обычной формы логина, но PlayerPadel должен быть в базе
            // Здесь нельзя загрузить из базы без сервиса, поэтому возвращаем null
            // и загружаем в сервисе при необходимости
            log.debug("Principal is UserDetails, needs to be loaded from database: {}",
                    ((UserDetails) principal).getUsername());
        }

        return null;
    }

    /**
     * Проверяет, аутентифицирован ли пользователь и является ли он игроком
     * @param principal объект principal
     * @return true если пользователь - игрок
     */
    public static boolean isPlayerAuthenticated(Object principal) {
        return extractPlayer(principal) != null;
    }
}