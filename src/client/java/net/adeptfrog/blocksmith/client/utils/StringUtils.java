package net.adeptfrog.blocksmith.client.utils;

import java.util.Arrays;
import java.util.stream.Collectors;

public class StringUtils {
    public static String capitalize(String str) {
        return Arrays.stream(str.split("\\s+"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    public static String rawFileName(String fileName) {
        if (fileName.lastIndexOf('.') > 0) return fileName.substring(0, fileName.lastIndexOf('.'));
        return fileName;
    }
}
