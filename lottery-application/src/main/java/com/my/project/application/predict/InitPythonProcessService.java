package com.my.project.application.predict;

import java.io.IOException;
import java.util.Map;

/**
 * InitPythonProcessService
 *
 * @author 刘强
 * @version 2025/10/28 19:51
 **/
public interface InitPythonProcessService {
    void init() throws IOException;

    String runInference(Map<String, Object> features);

    void shutdown();
}
