package com.my.project.service.support;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.my.project.persistence.entity.PredictRecord;
import java.io.ByteArrayOutputStream;

/**
 * KryoSerializerUtils  Kryo 序列化工具（线程安全）
 *
 * @author 刘强
 * @version 2026/07/17 11:30
 **/
public class KryoSerializerUtils {

    // ThreadLocal 保证每个线程有自己的 Kryo 实例（Kryo 非线程安全）
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false); // 不强制注册，方便扩展
        // 注册你需要序列化的类（提升性能）
        kryo.register(PredictRecord.class);
        return kryo;
    });

    public static byte[] serialize(Object obj) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Output output = new Output(baos)) {
            Kryo kryo = kryoThreadLocal.get();
            kryo.writeObject(output, obj);
            output.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Kryo serialization failed", e);
        }
    }

    public static <T> T deserialize(byte[] data, Class<T> clazz) {
        try (Input input = new Input(data)) {
            Kryo kryo = kryoThreadLocal.get();
            return kryo.readObject(input, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Kryo deserialization failed", e);
        }
    }
}
