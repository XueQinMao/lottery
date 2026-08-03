package com.my.project.python.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.IntStream;

/**
 * PythonConnectionPool
 *
 * @author 刘强
 * @version 2025/10/28 19:39
 **/
public class PythonConnectionPool {

    private static final Logger logger = LoggerFactory.getLogger(PythonConnectionPool.class);

    private BlockingQueue<PythonConnection> pool;


    public PythonConnectionPool(int size, String scriptName, String modelDir) throws IOException {
        pool = new ArrayBlockingQueue<>(size);
        IntStream.range(0, size).boxed().parallel().forEach(i->{
            try {
                pool.offer(new PythonConnection(scriptName, modelDir));
            }catch (IOException e){
                logger.error("初始化python连接失败", e);
            }

        });
    }

    public PythonConnection borrowConnection() throws InterruptedException {
        return pool.take(); // 如果没有可用连接，会阻塞等待
    }

    public Integer getSize(){
        return pool.size();
    }

    public void returnConnection(PythonConnection conn) {
        pool.offer(conn);
    }

    public void shutdown() {
        for (PythonConnection conn : pool) {
            conn.close();
        }
    }
}
