package io.github.r0mbaa.wms.core.support;

import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Извлечение значений из ответа, когда их нужно передать в следующий запрос сценария. */
public final class Json {

    private Json() {
    }

    public static <T> T read(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    public static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
