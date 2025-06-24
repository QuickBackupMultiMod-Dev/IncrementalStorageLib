package io.github.skydynamic.increment.storage.lib;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

public class HashTest {

    @Test
    void hashTest() throws IOException {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("testFile.txt");
        String hash = HashUtil.getHash(is);
        InputStream is1 = Thread.currentThread().getContextClassLoader().getResourceAsStream("testFile2.txt");
        String hash1 = HashUtil.getHash(is1);
        System.out.println("hash = " + hash);
        System.out.println("hash1 = " + hash1);
        assert !hash1.equals(hash);
    }
}
