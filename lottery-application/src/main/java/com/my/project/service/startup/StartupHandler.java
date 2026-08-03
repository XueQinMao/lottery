package com.my.project.service.startup;

import com.my.project.service.selection.pojo.bo.StartupContextBo;

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
