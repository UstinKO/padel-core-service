package com.padle.core.padelcoreservice.support;

import com.google.gson.JsonElement;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import java.util.EnumSet;
import java.util.Set;

/**
 * Переключает json-path (MockMvc {@code jsonPath(...)}) с json-smart на Gson до запуска тестов.
 * json-smart исключён из зависимостей (LFPT-0726: уязвимость без исправленной версии), а json-path
 * по умолчанию без него не работает. Регистрируется через
 * META-INF/services/org.junit.platform.launcher.LauncherSessionListener.
 */
public class JsonPathGsonDefaults implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        Configuration.setDefaults(new Configuration.Defaults() {
            private final JsonProvider jsonProvider = new JsonSmartLikeGsonProvider();
            private final MappingProvider mappingProvider = new GsonMappingProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }
        });
    }

    /** JSON {@code null} в объекте → Java {@code null}, как у json-smart (иначе doesNotExist() видит JsonNull). */
    private static class JsonSmartLikeGsonProvider extends GsonJsonProvider {
        @Override
        public Object getMapValue(Object obj, String key) {
            return jsonNullToNull(super.getMapValue(obj, key));
        }

        @Override
        public Object getArrayIndex(Object obj, int idx) {
            return jsonNullToNull(super.getArrayIndex(obj, idx));
        }

        private static Object jsonNullToNull(Object value) {
            return value instanceof JsonElement element && element.isJsonNull() ? null : value;
        }
    }
}
