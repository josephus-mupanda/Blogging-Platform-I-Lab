package org.example.bloggingplatformi.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

/**
 * Saves a JavaFX {@link Image} as a PNG file, for test screenshots.
 * SwingFXUtils and ImageIO would need extra modules the app does not use,
 * so this writes the PNG by hand with the JDK's zlib and CRC32 classes.
 */
public final class Png {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private Png() {
    }

    public static void write(Image image, Path target) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();

        // Each row is one filter byte (0 = none), then 3 bytes (R, G, B) per pixel.
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream zlib = new DeflaterOutputStream(compressed)) {
            byte[] row = new byte[1 + width * 3];
            int[] argb = new int[width];
            for (int y = 0; y < height; y++) {
                reader.getPixels(0, y, width, 1,
                        javafx.scene.image.PixelFormat.getIntArgbInstance(), argb, 0, width);
                row[0] = 0;
                for (int x = 0; x < width; x++) {
                    int pixel = argb[x];
                    row[1 + x * 3] = (byte) (pixel >> 16);
                    row[2 + x * 3] = (byte) (pixel >> 8);
                    row[3 + x * 3] = (byte) pixel;
                }
                zlib.write(row);
            }
        }

        ByteArrayOutputStream header = new ByteArrayOutputStream();
        writeInt(header, width);
        writeInt(header, height);
        header.write(8);  // bit depth
        header.write(2);  // colour type: RGB
        header.write(0);  // compression
        header.write(0);  // filter
        header.write(0);  // no interlace

        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        try (OutputStream out = Files.newOutputStream(target)) {
            out.write(SIGNATURE);
            chunk(out, "IHDR", header.toByteArray());
            chunk(out, "IDAT", compressed.toByteArray());
            chunk(out, "IEND", new byte[0]);
        }
    }

    private static void chunk(OutputStream out, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        writeInt(out, data.length);
        out.write(typeBytes);
        out.write(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(OutputStream out, int value) throws IOException {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }
}
