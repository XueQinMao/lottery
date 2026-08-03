package com.my.project.service.predict;

import java.io.IOException;

/**
 * ITrainModelService 模型训练
 *
 * @author 刘强
 * @version 2025/10/23 16:00
 **/
public interface ITrainModelService {
    /**
     * 训练所有的模型
     */
    void train() throws IOException, InterruptedException;
}
