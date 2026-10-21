package br.com.carrim.adapter.out.catalog;

import br.com.carrim.application.photos.PhotoLimitException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Set;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Decodes bounded raster input and emits fresh JPEG pixels, discarding metadata. */
public final class PhotoImageCodec {
    public static final int MAX_BYTES = 512000;
    private final Semaphore processing = new Semaphore(2);

    public byte[] normalize(byte[] input, String claimedType) {
        if (input == null
                || input.length == 0
                || input.length > MAX_BYTES
                || !Set.of("image/jpeg", "image/png").contains(claimedType))
            throw new IllegalArgumentException("Invalid image");
        if (!processing.tryAcquire()) throw new PhotoLimitException();
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("Invalid image");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!(format.equalsIgnoreCase("JPEG") && claimedType.equals("image/jpeg")
                        || format.equalsIgnoreCase("PNG") && claimedType.equals("image/png")))
                    throw new IllegalArgumentException("Invalid image");
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 16000000)
                    throw new IllegalArgumentException("Image dimensions exceed limit");
                var decoded = reader.read(0);
                if (decoded == null) throw new IllegalArgumentException("Invalid image");
                double scale = Math.min(1, 1024.0 / Math.max(width, height));
                var resized = new BufferedImage(
                        Math.max(1, (int) (width * scale)),
                        Math.max(1, (int) (height * scale)),
                        BufferedImage.TYPE_INT_RGB);
                var graphics = resized.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, resized.getWidth(), resized.getHeight());
                    graphics.setRenderingHint(
                            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(decoded, 0, 0, resized.getWidth(), resized.getHeight(), null);
                } finally {
                    graphics.dispose();
                    decoded.flush();
                }
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(resized, "jpeg", output)) throw new IllegalArgumentException("Unsupported image");
                resized.flush();
                if (output.size() > MAX_BYTES) throw new IllegalArgumentException("Image output too large");
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof PhotoLimitException limit) throw limit;
            throw new IllegalArgumentException("Invalid image");
        } finally {
            processing.release();
        }
    }
}
