package io.testfly.recording;

import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Rational;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Pure-Java MP4 video encoder powered by JCodec.
 *
 * <p>Encodes a sequence of {@link BufferedImage} frames into a standard H.264 / MPEG-4
 * container (.mp4) playable natively in HTML5 video elements, Allure video player,
 * and media players with zero external OS binaries (no native ffmpeg required).
 */
public final class Mp4Encoder {

    private Mp4Encoder() {}

    /**
     * Writes a sequence of image frames to an MP4 video file.
     *
     * @param frames ordered list of frames
     * @param output destination MP4 file
     * @param fps    frames per second
     * @throws IOException if encoding fails
     */
    public static void encode(List<BufferedImage> frames, File output, int fps) throws IOException {
        if (frames == null || frames.isEmpty()) return;

        SeekableByteChannel out = null;
        try {
            out = NIOUtils.writableChannel(output);
            AWTSequenceEncoder encoder = new AWTSequenceEncoder(out, Rational.R(Math.max(1, fps), 1));

            int targetWidth = frames.get(0).getWidth();
            int targetHeight = frames.get(0).getHeight();

            // Dimensions must be even for standard MPEG/H.264 macroblocks
            if (targetWidth % 2 != 0) targetWidth--;
            if (targetHeight % 2 != 0) targetHeight--;

            for (BufferedImage frame : frames) {
                BufferedImage normalized = toRgbEven(frame, targetWidth, targetHeight);
                encoder.encodeImage(normalized);
            }

            encoder.finish();
        } finally {
            NIOUtils.closeQuietly(out);
        }
    }

    private static BufferedImage toRgbEven(BufferedImage src, int width, int height) {
        if (src.getType() == BufferedImage.TYPE_3BYTE_BGR
                && src.getWidth() == width
                && src.getHeight() == height) {
            return src;
        }
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2d = copy.createGraphics();
        try {
            g2d.drawImage(src, 0, 0, width, height, null);
        } finally {
            g2d.dispose();
        }
        return copy;
    }
}
