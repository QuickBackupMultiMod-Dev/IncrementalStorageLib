package io.github.skydynamic.increment.storage.lib.util;

import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("unused")
public class HashUtil {
    private final static XXHashFactory XX_HASH_FACTORY = XXHashFactory.fastestInstance();
    private final static long SEED = 214114516411L;
    public final static int HASH_BLOCK_SIZE = Integer.parseInt(
            System.getProperty(
                    "incremental.hashBlockSize",
                    Integer.toString(1024 * 1024 * 5)
            )
    );

    public static @NotNull String getFileHash(Path file) throws IOException {
        FileInputStream is = new FileInputStream(file.toFile());
        String hash = getHash(is);
        is.close();
        return hash;
    }

    public static @NotNull String getHash(InputStream is) throws IOException {
        StreamingXXHash64 hash64 = XX_HASH_FACTORY.newStreamingHash64(SEED);
        byte[] bufc = new byte[HASH_BLOCK_SIZE];
        int len = is.read(bufc);
        while (len != -1) {
            hash64.update(bufc, 0, len);
            len = is.read(bufc);
        }
        return Long.toHexString(hash64.getValue());
    }

    private static @NotNull String bytesToHex(byte @NotNull [] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static byte @NotNull [] longToBytes(long l) {
        //分配缓冲区，单位为字节，一个long类型占8个字节，所以分配为8
        ByteBuffer byteBuffer = ByteBuffer.allocate(Long.SIZE / Byte.SIZE);
        //参数一为起始位置（不指定默认为0），参数二为所放的值
        byteBuffer.putLong(0, l);
        return byteBuffer.array();
    }
}
