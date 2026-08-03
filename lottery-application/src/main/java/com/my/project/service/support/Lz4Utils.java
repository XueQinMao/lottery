package com.my.project.service.support;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.nio.ByteBuffer;

/**
 * Lz4Utils LZ4 压缩/解压工具（含长度头）
 *
 * @author 刘强
 * @version 2026/07/17 11:31
 **/
public class Lz4Utils {

    private static final LZ4Factory factory = LZ4Factory.fastestInstance();
    private static final LZ4Compressor compressor = factory.fastCompressor();
    private static final LZ4FastDecompressor decompressor = factory.fastDecompressor();

    /**
     * 压缩并添加长度头（便于解压）
     * 格式：[原始长度(4字节)] + [LZ4压缩数据]
     */
    public static byte[] compressWithLength(byte[] src) {
        int maxCompressedLength = compressor.maxCompressedLength(src.length);
        byte[] compressed = new byte[maxCompressedLength];
        int compressedLength = compressor.compress(src, 0, src.length, compressed, 0, maxCompressedLength);

        // 构造最终数据：4字节长度头 + 压缩数据
        ByteBuffer buffer = ByteBuffer.allocate(4 + compressedLength);
        buffer.putInt(src.length);
        buffer.put(compressed, 0, compressedLength);
        return buffer.array();
    }

    /**
     * 解压（自动读取长度头）
     */
    public static byte[] decompressWithLength(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int originalLength = buffer.getInt();
        byte[] compressed = new byte[data.length - 4];
        buffer.get(compressed);

        byte[] restored = new byte[originalLength];
        decompressor.decompress(compressed, 0, restored, 0, originalLength);
        return restored;
    }
}
