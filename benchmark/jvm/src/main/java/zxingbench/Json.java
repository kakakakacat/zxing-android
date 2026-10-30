/*
 * Copyright (C) 2026 ZXing Android maintenance fork contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zxingbench;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON reader/writer, so the benchmark has no third-party dependency. */
final class Json {

  private Json() {
  }

  static final class Value {
    private final Object raw;

    private Value(Object raw) {
      this.raw = raw;
    }

    boolean isNull() {
      return raw == null;
    }

    String string() {
      return (String) raw;
    }

    double number() {
      return ((Number) raw).doubleValue();
    }

    @SuppressWarnings("unchecked")
    List<Value> array() {
      List<Object> list = (List<Object>) raw;
      List<Value> out = new ArrayList<>(list.size());
      for (Object o : list) {
        out.add(new Value(o));
      }
      return out;
    }

    @SuppressWarnings("unchecked")
    Value get(String key) {
      Map<String,Object> map = (Map<String,Object>) raw;
      return new Value(map.get(key));
    }
  }

  static Value parse(String s) {
    Parser p = new Parser(s);
    Object v = p.value();
    p.skipWhitespace();
    if (p.pos != s.length()) {
      throw new IllegalArgumentException("trailing content at " + p.pos);
    }
    return new Value(v);
  }

  /** Escapes a string for JSON output. */
  static String quote(String s) {
    if (s == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder(s.length() + 2);
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"': sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        case '\n': sb.append("\\n"); break;
        case '\r': sb.append("\\r"); break;
        case '\t': sb.append("\\t"); break;
        default:
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    return sb.append('"').toString();
  }

  private static final class Parser {
    private final String s;
    private int pos;

    Parser(String s) {
      this.s = s;
    }

    void skipWhitespace() {
      while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
        pos++;
      }
    }

    Object value() {
      skipWhitespace();
      char c = s.charAt(pos);
      switch (c) {
        case '{': return object();
        case '[': return array();
        case '"': return string();
        case 't': expect("true"); return Boolean.TRUE;
        case 'f': expect("false"); return Boolean.FALSE;
        case 'n': expect("null"); return null;
        default: return number();
      }
    }

    private void expect(String literal) {
      if (!s.startsWith(literal, pos)) {
        throw new IllegalArgumentException("expected " + literal + " at " + pos);
      }
      pos += literal.length();
    }

    private Map<String,Object> object() {
      Map<String,Object> map = new LinkedHashMap<>();
      pos++; // {
      skipWhitespace();
      if (s.charAt(pos) == '}') {
        pos++;
        return map;
      }
      while (true) {
        skipWhitespace();
        String key = string();
        skipWhitespace();
        if (s.charAt(pos++) != ':') {
          throw new IllegalArgumentException("expected : at " + (pos - 1));
        }
        map.put(key, value());
        skipWhitespace();
        char c = s.charAt(pos++);
        if (c == '}') {
          return map;
        }
        if (c != ',') {
          throw new IllegalArgumentException("expected , or } at " + (pos - 1));
        }
      }
    }

    private List<Object> array() {
      List<Object> list = new ArrayList<>();
      pos++; // [
      skipWhitespace();
      if (s.charAt(pos) == ']') {
        pos++;
        return list;
      }
      while (true) {
        list.add(value());
        skipWhitespace();
        char c = s.charAt(pos++);
        if (c == ']') {
          return list;
        }
        if (c != ',') {
          throw new IllegalArgumentException("expected , or ] at " + (pos - 1));
        }
      }
    }

    private String string() {
      if (s.charAt(pos) != '"') {
        throw new IllegalArgumentException("expected string at " + pos);
      }
      pos++;
      StringBuilder sb = new StringBuilder();
      while (true) {
        char c = s.charAt(pos++);
        if (c == '"') {
          return sb.toString();
        }
        if (c != '\\') {
          sb.append(c);
          continue;
        }
        char esc = s.charAt(pos++);
        switch (esc) {
          case '"': sb.append('"'); break;
          case '\\': sb.append('\\'); break;
          case '/': sb.append('/'); break;
          case 'b': sb.append('\b'); break;
          case 'f': sb.append('\f'); break;
          case 'n': sb.append('\n'); break;
          case 'r': sb.append('\r'); break;
          case 't': sb.append('\t'); break;
          case 'u':
            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
            pos += 4;
            break;
          default:
            throw new IllegalArgumentException("bad escape \\" + esc + " at " + (pos - 1));
        }
      }
    }

    private Double number() {
      int start = pos;
      while (pos < s.length() && "-+.eE0123456789".indexOf(s.charAt(pos)) >= 0) {
        pos++;
      }
      return Double.valueOf(s.substring(start, pos));
    }
  }
}
