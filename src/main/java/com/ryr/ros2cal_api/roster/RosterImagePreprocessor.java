package com.ryr.ros2cal_api.roster;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

@Component
public class RosterImagePreprocessor {

    private static final int MIN_WIDTH = 1500;
    private static final int SCALE_FACTOR = 2;

    public byte[] preparePng(byte[] inputBytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(inputBytes));
        if (image == null) {
            throw new IOException("Unsupported image content");
        }
        BufferedImage resized = image;
        if (image.getWidth() < MIN_WIDTH) {
            int targetWidth = image.getWidth() * SCALE_FACTOR;
            int targetHeight = image.getHeight() * SCALE_FACTOR;
            BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = scaled.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(image, 0, 0, targetWidth, targetHeight, null);
            g2d.dispose();
            resized = scaled;
        }

        BufferedImage rgb = new BufferedImage(resized.getWidth(), resized.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.drawImage(resized, 0, 0, null);
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(rgb, "png", out)) {
            throw new IOException("Failed to write PNG");
        }
        return out.toByteArray();
    }
}
