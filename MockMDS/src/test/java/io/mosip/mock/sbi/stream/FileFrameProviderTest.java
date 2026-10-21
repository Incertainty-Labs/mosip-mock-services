package io.mosip.mock.sbi.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileFrameProviderTest {

	@TempDir
	Path dir;

	@Test
	void jpegFolderPlaysFilesInNameOrderAndLoops() throws IOException {
		Files.write(dir.resolve("frame_002.jpg"), jpeg(20));
		Files.write(dir.resolve("frame_001.jpg"), jpeg(10));
		Files.write(dir.resolve("frame_003.jpeg"), jpeg(30));
		Files.write(dir.resolve("notes.txt"), "not a frame".getBytes());

		try (FrameProvider provider = FrameProviders.open(FaceStreamSource.parse("file:" + dir))) {
			assertEquals(List.of(10, 20, 30, 10, 20), widthsOf(provider, 5));
		}
	}

	@Test
	void mjpegFilePlaysEachConcatenatedJpegAndLoops() throws IOException {
		ByteArrayOutputStream mjpeg = new ByteArrayOutputStream();
		mjpeg.write(jpeg(10));
		mjpeg.write(jpeg(20));
		mjpeg.write(jpeg(30));
		Path file = dir.resolve("clip.mjpeg");
		Files.write(file, mjpeg.toByteArray());

		try (FrameProvider provider = FrameProviders.open(FaceStreamSource.parse("file:" + file))) {
			assertEquals(List.of(10, 20, 30, 10), widthsOf(provider, 4));
		}
	}

	@Test
	void folderWithoutJpegsIsRejected() throws IOException {
		Files.write(dir.resolve("notes.txt"), "not a frame".getBytes());

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> FrameProviders.open(FaceStreamSource.parse("file:" + dir)));

		assertEquals("No .jpg or .jpeg frames in " + dir, error.getMessage());
	}

	@Test
	void missingFileIsRejected() {
		Path missing = dir.resolve("missing.mjpeg");

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> FrameProviders.open(FaceStreamSource.parse("file:" + missing)));

		assertEquals("Stream file not found: " + missing, error.getMessage());
	}

	@Test
	void mjpegFileWithoutFramesIsRejected() throws IOException {
		Path file = dir.resolve("empty.mjpeg");
		Files.write(file, new byte[] { 1, 2, 3 });

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> FrameProviders.open(FaceStreamSource.parse("file:" + file)));

		assertEquals("No JPEG frames in " + file, error.getMessage());
	}

	private static List<Integer> widthsOf(FrameProvider provider, int count) throws IOException {
		List<Integer> widths = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			widths.add(ImageIO.read(new ByteArrayInputStream(provider.nextFrame())).getWidth());
		}
		return widths;
	}

	/** A small JPEG whose width identifies it. */
	private static byte[] jpeg(int width) throws IOException {
		BufferedImage image = new BufferedImage(width, 8, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
		return out.toByteArray();
	}
}
