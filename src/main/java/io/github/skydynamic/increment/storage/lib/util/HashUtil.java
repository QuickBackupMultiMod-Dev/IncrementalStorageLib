package io.github.skydynamic.increment.storage.lib.util;

import net.jpountz.xxhash.XXHashFactory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("unused")
public class HashUtil {
    private final static XXHashFactory XX_HASH_FACTORY = XXHashFactory.fastestInstance();

    public static @NotNull String getFileHash(Path file) throws IOException {
        byte[] fileData;
        byte[] digest;
        fileData = Files.readAllBytes(file);
        digest = longToBytes(XX_HASH_FACTORY.hash64().hash(fileData, 0, fileData.length, 0));
        return bytesToHex(digest);
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
