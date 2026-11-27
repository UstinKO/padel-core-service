package com.padle.core.application.identity;

import java.util.Locale;

/** Предпочтение игрока на своей половине корта, лицом к сетке. */
public enum PlayingSide {
    LEFT, RIGHT, EITHER;

    public String getMessageKey() { return "playingSide." + name().toLowerCase(Locale.ROOT); }
}
