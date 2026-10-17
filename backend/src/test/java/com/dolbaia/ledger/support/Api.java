package com.dolbaia.ledger.support;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Terse calls to the API as the signed-in owner, with bodies built from key/value pairs. */
public final class Api {

    private final MockMvc mvc;
    private final RequestPostProcessor owner;

    public Api(MockMvc mvc, RequestPostProcessor owner) {
        this.mvc = mvc;
        this.owner = owner;
    }

    /** {"k1": "v1", "k2": v2, ...}; strings are quoted, everything else written as is. */
    public static String body(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) values.put((String) pairs[index], pairs[index + 1]);
        StringBuilder json = new StringBuilder("{");
        values.forEach((key, value) -> {
            if (json.length() > 1) json.append(',');
            json.append('"').append(key).append("\":");
            if (value == null) json.append("null");
            else if (value instanceof String text) json.append('"').append(text.replace("\"", "\\\"")).append('"');
            else json.append(value);
        });
        return json.append('}').toString();
    }

    public ResultActions get(String path) throws Exception {
        return perform(MockMvcRequestBuilders.get(path));
    }

    public ResultActions post(String path, String json) throws Exception {
        return perform(MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public ResultActions post(String path) throws Exception {
        return perform(MockMvcRequestBuilders.post(path));
    }

    public ResultActions put(String path, String json) throws Exception {
        return perform(MockMvcRequestBuilders.put(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public ResultActions delete(String path) throws Exception {
        return perform(MockMvcRequestBuilders.delete(path));
    }

    public ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.with(owner));
    }

    /** POSTs and returns a value from the JSON response, such as "$.id". */
    public String create(String path, String json, String jsonPath) throws Exception {
        String response = post(path, json).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, jsonPath).toString();
    }

    public String read(ResultActions result, String jsonPath) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath).toString();
    }
}
