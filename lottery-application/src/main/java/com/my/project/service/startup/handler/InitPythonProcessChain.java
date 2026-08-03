package com.my.project.service.startup.handler;

import com.my.project.service.selection.pojo.bo.StartupContextBo;
import com.my.project.service.predict.IInitPythonProcessService;
import org.springframework.stereotype.Component;

/**
 * TrainModelChain
 *
 * @author 刘强
 * @version 2025/10/27 16:04
 **/
@Component
public class InitPythonProcessChain extends AbstractStartupChain{

    private final IInitPythonProcessService pythonProcessService;

    public InitPythonProcessChain(IInitPythonProcessService initPythonProcessService) {
        this.pythonProcessService = initPythonProcessService;
    }

    @Override
    protected String getStepName() {
        return "初始化python脚本";
    }

    @Override
    protected void doHandle(StartupContextBo<Object> context) {
        try {
            pythonProcessService.init();
        }catch (Exception e){
            context.setSuccess(false);
            context.setErrorMessage(e.getMessage());
        }

    }
}
