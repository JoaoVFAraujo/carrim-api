package br.com.carrim.adapter.out.catalog;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PhotoImageCodecTests {
    static byte[] image(String format, int width, int height) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    @Test
    void normalizesSupportedRasterAndLimitsOutputDimensions() throws Exception {
        var codec = new PhotoImageCodec();
        for (String format : new String[] {"png", "jpeg"}) {
            var original = image(format, 1600, 400);
            byte[] result = codec.normalize(original, "image/" + format);
            var output = ImageIO.read(new ByteArrayInputStream(result));
            assertEquals(1024, output.getWidth());
            assertEquals(256, output.getHeight());
            assertTrue(result.length <= 512000);
            assertEquals((byte) 0xff, result[0]);
            assertEquals((byte) 0xd8, result[1]);
        }
    }

    @Test
    void rejectsTypeSpoofingCorruptionByteAndPixelBombs() throws Exception {
        var codec = new PhotoImageCodec();
        assertThrows(IllegalArgumentException.class, () -> codec.normalize(image("png", 5, 5), "image/jpeg"));
        assertThrows(IllegalArgumentException.class, () -> codec.normalize("not an image".getBytes(), "image/png"));
        assertThrows(IllegalArgumentException.class, () -> codec.normalize(new byte[512001], "image/jpeg"));
        assertThrows(IllegalArgumentException.class, () -> codec.normalize(image("png", 4097, 1), "image/png"));
        assertThrows(IllegalArgumentException.class, () -> codec.normalize(image("png", 2, 2), "image/svg+xml"));
    }

    @Test
    void createsFreshJpegWithoutExifMetadata() throws Exception {
        byte[] jpeg = image("jpeg", 20, 30);
        byte[] marker = new byte[] {(byte) 0xff, (byte) 0xe1, 0, 12, 'E', 'x', 'i', 'f', 0, 0, 0, 0, 0, 0};
        var out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(marker);
        out.write(jpeg, 2, jpeg.length - 2);
        byte[] normalized = new PhotoImageCodec().normalize(out.toByteArray(), "image/jpeg");
        assertFalse(new String(normalized, java.nio.charset.StandardCharsets.ISO_8859_1).contains("Exif"));
    }
}
