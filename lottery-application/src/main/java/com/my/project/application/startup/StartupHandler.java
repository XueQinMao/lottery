package com.my.project.application.startup;

import com.my.project.application.selection.model.StartupContextBo;

/**
 * StartupHandler
 *
 * @author 刘强
 * @version 2025/09/01 15:21
 **/
public interface StartupHandler {

    void handle(StartupContextBo<Object> context);

    void setNext(StartupHandler handler);
}
