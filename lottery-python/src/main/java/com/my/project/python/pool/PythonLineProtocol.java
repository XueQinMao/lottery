package com.my.project.python.pool;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;

final class PythonLineProtocol {
    private final BufferedReader reader;
    private final BufferedWriter writer;

    PythonLineProtocol(BufferedReader reader, BufferedWriter writer) {
        this.reader = reader;
        this.writer = writer;
    }

    void handshake() throws IOException {
        JSONObject init = parse(reader.readLine());
        if (!"init_complete".equals(init.getString("type"))) throw new IOException("Python初始化失败: " + init);
        JSONObject check = parse(exchange(Map.of("type", "init_check")));
        if (!"initialized".equals(check.getString("status"))) throw new IOException("Python初始化检查失败: " + check);
    }

    String request(Map<String, Object> request) throws IOException {
        String line = exchange(request);
        JSONObject response = parse(line);
        if (response.containsKey("error") || "error".equals(response.getString("status")))
            throw new IOException("Python预测返回错误: " + response);
        return line;
    }

    private String exchange(Map<String, Object> request) throws IOException {
        writer.write(JSONObject.toJSONString(request));
        writer.newLine();
        writer.flush();
        return reader.readLine();
    }

    private static JSONObject parse(String line) throws IOException {
        if (line == null) throw new IOException("Python服务连接已断开（EOF）");
        try {
            JSONObject result = JSONObject.parseObject(line);
            if (result == null) throw new IOException("Python返回空响应");
            return result;
        } catch (RuntimeException e) {
            throw new IOException("Python响应不是有效JSON对象: " + StringUtils.left(line, 512), e);
        }
    }

}
