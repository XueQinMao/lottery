package com.my.project.python.pool;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 显式指定本地模型目录才运行，不修改模型、不访问业务数据库。 */
@EnabledIfSystemProperty(named = "python.real-model-dir", matches = ".+")
class PythonConnectionRealModelTest {
    @Test void jsonProtocolReturnsSameRealPredictionAsBaseline() throws Exception {
        Path comparison = Path.of("target/stability/comparison");
        var environment = JSONObject.parseObject(Files.readString(comparison.resolve("environment.json")));
        var request = environment.getJSONArray("inputs").getJSONObject(0).getInnerMap();
        var baseline = Files.readAllLines(comparison.resolve("baseline.jsonl")).stream()
                .filter(line -> line.contains("\"index\": 0,")).map(JSONObject::parseObject).findFirst().orElseThrow()
                .getJSONObject("response");
        var pool = new PythonConnectionPool(2, "predict.py", System.getProperty("python.real-model-dir"));
        try {
            for (int i = 0; i < 4; i++) {
                var connection = pool.borrowConnection();
                try {
                    var response = JSONObject.parseObject(connection.execute(request));
                    assertEquals(baseline.getDoubleValue("probability"), response.getDoubleValue("probability"), 1e-12);
                    assertEquals(baseline.getString("reason"), response.getString("reason"));
                } finally { pool.returnConnection(connection); }
            }
            assertEquals(2, pool.getSize());
        } finally { pool.shutdown(); }
    }
}
