package com.my.project.python.pool;

import com.my.project.python.support.ResourcePathUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PythonResourceExtractionTest {
    @Test void jarExtractionIncludesEveryPredictionImport() throws Exception {
        String extracted = ReflectionTestUtils.invokeMethod(ResourcePathUtils.class, "extractPythonScriptFromJar", "predict.py");
        assertNotNull(extracted);
        Path directory = Path.of(extracted).getParent();
        for (String name : new String[]{"predict.py", "ssq_features.py", "prediction_diagnostics.py"}) {
            try (var resource = ResourcePathUtils.class.getClassLoader().getResourceAsStream("python/" + name)) {
                assertNotNull(resource);
                assertArrayEquals(resource.readAllBytes(), Files.readAllBytes(directory.resolve(name)));
            }
        }
    }
}
