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

            int targetWidth = frames.getFirst().getWidth();
            int targetHeight = frames.getFirst().getHeight();

            // Dimensions must be even for standard MPEG/H.264 macroblocks
            if (targetWidth % 2 != 0) targetWidth--;
            if (targetHeight % 2 != 0) targetHeight--;

            BufferedImage workBuffer = null;
            Graphics2D g2d = null;

            try {
                for (BufferedImage frame : frames) {
                    if (frame.getType() == BufferedImage.TYPE_3BYTE_BGR
                            && frame.getWidth() == targetWidth
                            && frame.getHeight() == targetHeight) {
                        encoder.encodeImage(frame);
                    } else {
                        if (workBuffer == null) {
                            workBuffer = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_3BYTE_BGR);
                            g2d = workBuffer.createGraphics();
                        }
                        g2d.drawImage(frame, 0, 0, targetWidth, targetHeight, null);
                        encoder.encodeImage(workBuffer);
                    }
                }
            } finally {
                if (g2d != null) {
                    g2d.dispose();
                }
            }

            encoder.finish();
        } finally {
            NIOUtils.closeQuietly(out);
        }
    }
}
