package com.padle.core.padelcoreservice.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.status.Status;
import com.github.loki4j.logback.Loki4jAppender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.mock.env.MockEnvironment;
import ch.qos.logback.classic.Level;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class LokiLoggingConfigurationTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {
            "", "default", "dev", "development",
            "test", "staging", "prod", "production"
    })
    void disabledLokiDoesNotAttachOrSendRequests(String profile) throws Exception {
        checkLoki(false, profile, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "default", "dev", "development",
            "test", "staging", "prod", "production"
    })
    void enabledLokiAttachesAndSendsMessage(String profile) throws Exception {
        checkLoki(true, profile, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "default", "dev", "development",
            "test", "staging", "prod", "production"
    })
    void missingLokiPropertyDoesNotAttachOrSendRequests(String profile)
            throws Exception {
        checkLoki(false, profile, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "prod"})
    void disabledLokiRemainsDisabledAfterReload(String profile) throws Exception {
        checkLoki(false, profile, true, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "prod"})
    void enabledLokiStopsAfterReloadWithDisabledProperty(String profile)
            throws Exception {
        checkLoki(true, profile, true, true);
    }


    private void checkLoki(boolean enabled,
                           String profile,
                           boolean propertyPresent) throws Exception {
        checkLoki(enabled, profile, propertyPresent, false);
    }

    private void checkLoki(boolean enabled, String profile, boolean propertyPresent,
                           boolean reloadWithLokiDisabled) throws Exception {
        BlockingQueue<ReceivedPush> pushes = new LinkedBlockingQueue<>();
        String marker = "loki-control-" + UUID.randomUUID();
        Path deliverySignal = tempDir.resolve("loki-delivered");

        var server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );

        server.createContext("/", exchange -> {
            try {
                String body = new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                );

                pushes.add(new ReceivedPush(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        body
                ));

                exchange.sendResponseHeaders(204, -1);

                if (body.contains(marker)) {
                    Files.writeString(deliverySignal, "delivered");
                }
            } finally {
                exchange.close();
            }
        });

        try {
            server.start();

            String url = "http://127.0.0.1:"
                    + server.getAddress().getPort()
                    + "/loki/api/v1/push";

            runProbe(
                    url, enabled, marker, pushes,
                    profile, propertyPresent, reloadWithLokiDisabled
            );

            if (!enabled) {
                assertThat(pushes)
                        .as("При выключенном Loki HTTP-запросов нет")
                        .isEmpty();
            }
        } finally {
            server.stop(0);
        }
    }

    private void runProbe(String url,
                          boolean enabled,
                          String marker,
                          BlockingQueue<ReceivedPush> pushes,
                          String profile,
                          boolean propertyPresent,
                          boolean reloadWithLokiDisabled) throws Exception {
        String executable = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT)
                .contains("win") ? "java.exe" : "java";

        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Path arguments = tempDir.resolve("java.args");
        Path output = tempDir.resolve("probe.log");

        String classpath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path")
        );

        Files.write(arguments, List.of(
                "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8",
                "-cp",
                quoteArgument(classpath),
                LoggingProbe.class.getName(),
                quoteArgument(tempDir.resolve("logs").toString()),
                url,
                Boolean.toString(enabled),
                marker,
                quoteArgument(profile),
                Boolean.toString(propertyPresent),
                Boolean.toString(reloadWithLokiDisabled),
                quoteArgument(tempDir.resolve("loki-delivered").toString())

        ));

        var builder = new ProcessBuilder(
                java.toString(), "@" + arguments
        ).redirectErrorStream(true)
                .redirectOutput(output.toFile());

        builder.environment().keySet().removeIf(name -> {
            String key = name.toUpperCase(Locale.ROOT);
            return key.startsWith("LOKI_")
                    || key.equals("JAVA_TOOL_OPTIONS")
                    || key.equals("_JAVA_OPTIONS")
                    || key.equals("JDK_JAVA_OPTIONS");
        });

        var process = builder.start();
        try {
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS))
                        .as("Дочерняя JVM остановлена после превышения таймаута")
                        .isTrue();
            }

            String diagnostics = Files.readString(output);

            assertThat(finished)
                    .as("Дочерняя JVM должна завершиться за 30 секунд:%n%s", diagnostics)
                    .isTrue();

            assertThat(process.exitValue())
                    .as("Проверка основной конфигурации:%n%s", diagnostics)
                    .isZero();

            if (enabled) {
                ReceivedPush push = pushes.poll(10, TimeUnit.SECONDS);

                assertThat(push)
                        .as("Включённый Loki отправляет HTTP-запрос:%n%s", diagnostics)
                        .isNotNull();

                assertThat(push.method()).isEqualTo("POST");
                assertThat(push.path()).isEqualTo("/loki/api/v1/push");
                assertThat(push.body()).contains(marker);
                if (reloadWithLokiDisabled) {
                    assertThat(pushes)
                            .as("Кроме начального контрольного запроса других HTTP-запросов нет")
                            .isEmpty();
                }
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static String quoteArgument(String value) {
        return "\"" + value.replace('\\', '/') + "\"";
    }

    private record ReceivedPush(String method, String path, String body) {
    }

    private static class ProbeLoggingSystem extends LogbackLoggingSystem {

        ProbeLoggingSystem() {
            super(LoggingProbe.class.getClassLoader());
        }

        void reload(MockEnvironment environment) {
            loadConfiguration(
                    new LoggingInitializationContext(environment),
                    "classpath:logback-spring.xml",
                    null
            );
        }
    }

    public static class LoggingProbe {
        public static void main(String[] args) throws InterruptedException {
            boolean reloadWithLokiDisabled = Boolean.parseBoolean(args[6]);
            boolean enabled = Boolean.parseBoolean(args[2]);
            String marker = args[3];

            boolean propertyPresent = Boolean.parseBoolean(args[5]);

            var environment = new MockEnvironment()
                    .withProperty("logging.loki.url", args[1])
                    .withProperty("logging.loki.batchTimeoutMs", "100")
                    .withProperty("logging.file.path", args[0]);

            if (propertyPresent) {
                environment.withProperty(
                        "logging.loki.enabled", Boolean.toString(enabled)
                );
            } else {
                assertThat(environment.containsProperty("logging.loki.enabled"))
                        .as("Свойство отсутствует: проверяется умолчание XML")
                        .isFalse();
            }

            String profile = args[4];
            if (!profile.isBlank()) {
                environment.setActiveProfiles(profile);
            }

            var loggingSystem = new ProbeLoggingSystem();
            var context = (LoggerContext) LoggerFactory.getILoggerFactory();

            try {
                loggingSystem.beforeInitialize();
                loggingSystem.initialize(
                        new LoggingInitializationContext(environment),
                        "classpath:logback-spring.xml",
                        null
                );

                var logger = context.getLogger("com.padle.core");
                assertOtherLoggingSettings(context, profile);

                if (enabled) {
                    var appenders = new ArrayList<Appender<ILoggingEvent>>();
                    logger.iteratorForAppenders().forEachRemaining(appenders::add);

                    assertThat(appenders)
                            .filteredOn(appender -> appender instanceof Loki4jAppender)
                            .as("При включённом Loki подключён один запущенный аппендер")
                            .singleElement()
                            .satisfies(appender -> {
                                assertThat(appender.getName()).isEqualTo("LOKI");
                                assertThat(appender.isStarted()).isTrue();
                            });
                } else {
                    assertThat(logger.getAppender("LOKI"))
                            .as("При logging.loki.enabled=false Loki не подключается")
                            .isNull();
                }

                logger.info(marker);

                if (enabled) {
                    Path deliverySignal = Path.of(args[7]);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

                    while (!Files.exists(deliverySignal) && System.nanoTime() < deadline) {
                        Thread.sleep(20);
                    }

                    assertThat(Files.exists(deliverySignal))
                            .as("HTTP-сервер получил контрольное сообщение за 10 секунд")
                            .isTrue();
                }

                Thread.sleep(500);

                assertThat(context.getStatusManager().getCopyOfStatusList())
                        .filteredOn(status -> status.getLevel() == Status.ERROR)
                        .as("Основная конфигурация не создаёт ERROR-статусов")
                        .isEmpty();

                if (reloadWithLokiDisabled) {
                    var previousLokiAppender = logger.getAppender("LOKI");
                    if (enabled) {
                        assertThat(previousLokiAppender).isNotNull();
                    }

                    var previousFileAppender = logger.getAppender("FILE");
                    assertThat(previousFileAppender).isNotNull();

                    environment.withProperty("logging.loki.enabled", "false");
                    loggingSystem.reload(environment);
                    assertOtherLoggingSettings(context, profile);

                    if (enabled) {
                        assertThat(previousLokiAppender.isStarted())
                                .as("Прежний Loki-аппендер остановлен после перезагрузки")
                                .isFalse();
                    }

                    var currentFileAppender = logger.getAppender("FILE");
                    assertThat(currentFileAppender)
                            .as("Повторная загрузка XML создаёт новый файловый аппендер")
                            .isNotNull()
                            .isNotSameAs(previousFileAppender);

                    assertThat(previousFileAppender.isStarted()).isFalse();
                    assertThat(currentFileAppender.isStarted()).isTrue();

                    assertThat(logger.getAppender("LOKI"))
                            .as("После перезагрузки с logging.loki.enabled=false Loki не подключён")
                            .isNull();

                    logger.info(marker + "-after-reload");
                    Thread.sleep(500);

                    assertThat(context.getStatusManager().getCopyOfStatusList())
                            .filteredOn(status -> status.getLevel() == Status.ERROR)
                            .as("После повторной загрузки нет ERROR-статусов")
                            .isEmpty();
                }
            } finally {
                loggingSystem.cleanUp();
                context.stop();
            }
        }

        private static void assertOtherLoggingSettings(
                LoggerContext context, String profile) {
            var logger = context.getLogger("com.padle.core");

            assertThat(logger.getLevel()).isEqualTo(Level.INFO);
            assertThat(logger.getEffectiveLevel()).isEqualTo(Level.INFO);
            assertThat(logger.isAdditive()).isFalse();

            var appenders = new ArrayList<Appender<ILoggingEvent>>();
            logger.iteratorForAppenders().forEachRemaining(appenders::add);

            var expectedNames = new ArrayList<>(List.of(
                    "CONSOLE", "FILE", "ERROR_FILE", "JSON_FILE"
            ));
            if (profile.equals("prod") || profile.equals("production")) {
                expectedNames.add("TELEGRAM");
            }

            assertThat(appenders)
                    .filteredOn(appender -> !appender.getName().equals("LOKI"))
                    .extracting(Appender::getName)
                    .containsExactlyInAnyOrderElementsOf(expectedNames);
        }
    }
}
