package com.padle.core.padelcoreservice.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaskingConverter extends ClassicConverter {

    // Email: bl***@g****.com
    // Группы: (1)первые 2 символа локальной части  (2)первые 1-2 символа домена  (3)TLD (.com, .ar и т.д.)
    private static final Pattern EMAIL = Pattern.compile(
            "([a-zA-Z0-9._%+\\-]{1,2})[a-zA-Z0-9._%+\\-]*@([a-zA-Z0-9\\-]{1,2})[a-zA-Z0-9\\-]*(\\.[a-zA-Z]{2,})",
            Pattern.CASE_INSENSITIVE
    );

    // Телефон: +549****67
    private static final Pattern PHONE = Pattern.compile(
            "(\\+\\d{2,3})\\d{4,8}(\\d{2})(?=\\D|$)"
    );

    // Пароль в JSON: "password":"secret" → "password":"***"
    private static final Pattern PASSWORD_JSON = Pattern.compile(
            "(\"password\"\\s*:\\s*\")([^\"]+)(\")",
            Pattern.CASE_INSENSITIVE
    );

    // Пароль в форме: password=secret → password=***
    private static final Pattern PASSWORD_FORM = Pattern.compile(
            "(password=)([^&\\s]+)",
            Pattern.CASE_INSENSITIVE
    );

    // JWT: Bearer eyJ... → Bearer [JWT_MASKED]
    private static final Pattern JWT = Pattern.compile(
            "(Bearer\\s+)[A-Za-z0-9\\-_]+\\.[A-Za-z0-9\\-_]+\\.[A-Za-z0-9\\-_]+"
    );

    // Токен партнёра: PRT_ABC123 → PRT_[MASKED]
    private static final Pattern PARTNER_TOKEN = Pattern.compile(
            "(PRT_)[A-Z0-9]{10,}"
    );

    // Telegram: @usuario → @us***
    private static final Pattern TELEGRAM = Pattern.compile(
            "(telegram[Uu]sername[\"\\s:=]+)(@[a-zA-Z0-9_]{3,})",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String convert(ILoggingEvent event) {
        String msg = event.getFormattedMessage();
        if (msg == null || msg.isEmpty()) return msg;

        msg = maskEmail(msg);
        msg = maskPhone(msg);
        msg = PASSWORD_JSON.matcher(msg).replaceAll("$1***$3");
        msg = PASSWORD_FORM.matcher(msg).replaceAll("$1***");
        msg = JWT.matcher(msg).replaceAll("$1[JWT_MASKED]");
        msg = PARTNER_TOKEN.matcher(msg).replaceAll("$1[MASKED]");
        msg = maskTelegram(msg);
        return msg;
    }

    // bl***@g****.com
    private String maskEmail(String input) {
        Matcher m = EMAIL.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String result = m.group(1) + "***@" + m.group(2) + "****" + m.group(3);
            m.appendReplacement(sb, Matcher.quoteReplacement(result));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String maskPhone(String input) {
        Matcher m = PHONE.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, m.group(1) + "****" + m.group(2));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String maskTelegram(String input) {
        Matcher m = TELEGRAM.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String username = m.group(2);
            String masked = username.length() > 3
                    ? username.substring(0, 3) + "***"
                    : "@***";
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}