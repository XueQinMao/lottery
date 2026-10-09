package com.my.project.application.startup.handler;

import com.my.project.application.selection.model.StartupContextBo;
import com.my.project.application.predict.InitPythonProcessService;
import org.springframework.stereotype.Component;

/**
 * TrainModelChain
 *
 * @author 刘强
 * @version 2025/10/27 16:04
 **/
@Component
public class InitPythonProcessChain extends AbstractStartupChain{

    private final InitPythonProcessService pythonProcessService;

    public InitPythonProcessChain(InitPythonProcessService initPythonProcessService) {
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
