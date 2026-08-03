package com.my.project.service.predict;

import java.io.IOException;
import java.util.Map;

/**
 * IInitPythonProcessService
 *
 * @author 刘强
 * @version 2025/10/28 19:51
 **/
public interface IInitPythonProcessService {
    void init() throws IOException;

    String runInference(Map<String, Object> features);

    void shutdown();
}
