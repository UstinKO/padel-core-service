package com.padle.core.padelcoreservice.logging;

import java.util.concurrent.LinkedBlockingQueue;

public class TelegramAlertQueue {
    // Ёмкость 200: при переполнении новые ошибки молча дропаются (offer)
    public static final LinkedBlockingQueue<String> QUEUE = new LinkedBlockingQueue<>(200);
}
