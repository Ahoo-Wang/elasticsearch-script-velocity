/*
 *  Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package me.ahoo.elasticsearch.script.velocity;

import org.elasticsearch.script.Script;
import org.elasticsearch.xcontent.json.JsonStringEncoder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * How values written by {@code $references} are encoded, chosen from the script's {@code content_type} option the
 * same way the mustache engine does: JSON string escaping by default, form URL encoding for
 * {@code application/x-www-form-urlencoded}, and no encoding for {@code text/plain}.
 */
enum ReferenceEncoder {
    NONE {
        @Override
        String encode(String value) {
            return value;
        }
    },
    JSON {
        @Override
        String encode(String value) {
            return new String(JsonStringEncoder.getInstance().quoteAsString(value));
        }
    },
    FORM_URL {
        @Override
        String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    };

    abstract String encode(String value);

    static ReferenceEncoder fromOptions(Map<String, String> options) {
        String mediaType = options == null ? null : options.get(Script.CONTENT_TYPE_OPTION);
        if (mediaType == null) {
            return JSON;
        }
        return switch (mediaType) {
            case "application/json", "application/json; charset=UTF-8", "application/json;charset=utf-8" -> JSON;
            case "text/plain" -> NONE;
            case "application/x-www-form-urlencoded" -> FORM_URL;
            default -> throw new IllegalArgumentException("No encoder found for media type [" + mediaType + "]");
        };
    }
}
