package io.mosip.mock.sbi.stream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Opens the {@link FrameProvider} for a configured {@link FaceStreamSource}. */
public final class FrameProviders {

	private FrameProviders() {
	}

	/**
	 * @param source the configured source
	 * @return a provider for {@code webcam} and {@code file:} sources
	 * @throws IllegalArgumentException if the camera cannot be opened, or the file or folder is missing or holds no
	 *                                  JPEG frames
	 * @throws IOException              if the frames cannot be read
	 */
	public static FrameProvider open(FaceStreamSource source) throws IOException {
		if (source.kind() == FaceStreamSource.Kind.WEBCAM) {
			return WebcamFrameProvider.open(source.cameraIndex());
		}
		if (source.kind() != FaceStreamSource.Kind.FILE) {
			throw new IllegalArgumentException("No frame provider for a " + source.kind() + " source");
		}
		Path path = source.path();
		if (Files.isDirectory(path)) {
			return new LoopingFrameProvider(readJpegFolder(path));
		}
		if (!Files.isRegularFile(path)) {
			throw new IllegalArgumentException("Stream file not found: " + path);
		}
		List<byte[]> frames = splitMjpeg(Files.readAllBytes(path));
		if (frames.isEmpty()) {
			throw new IllegalArgumentException("No JPEG frames in " + path);
		}
		return new LoopingFrameProvider(frames);
	}

	private static List<byte[]> readJpegFolder(Path folder) throws IOException {
		List<Path> files;
		try (Stream<Path> listing = Files.list(folder)) {
			files = listing.filter(FrameProviders::isJpeg).sorted().toList();
		}
		if (files.isEmpty()) {
			throw new IllegalArgumentException("No .jpg or .jpeg frames in " + folder);
		}
		List<byte[]> frames = new ArrayList<>();
		for (Path file : files) {
			frames.add(Files.readAllBytes(file));
		}
		return frames;
	}

	private static boolean isJpeg(Path file) {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		return Files.isRegularFile(file) && (name.endsWith(".jpg") || name.endsWith(".jpeg"));
	}

	/**
	 * Splits an MJPEG file (JPEG images back to back, as written by {@code ffmpeg -c:v mjpeg}) at each start
	 * marker {@code FF D8 FF} up to the next end marker {@code FF D9}.
	 */
	static List<byte[]> splitMjpeg(byte[] data) {
		List<byte[]> frames = new ArrayList<>();
		int start = indexOf(data, 0, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
		while (start >= 0) {
			int end = indexOf(data, start + 3, (byte) 0xFF, (byte) 0xD9);
			if (end < 0) {
				break;
			}
			byte[] frame = new byte[end + 2 - start];
			System.arraycopy(data, start, frame, 0, frame.length);
			frames.add(frame);
			start = indexOf(data, end + 2, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
		}
		return frames;
	}

	private static int indexOf(byte[] data, int from, byte... pattern) {
		outer: for (int i = from; i <= data.length - pattern.length; i++) {
			for (int j = 0; j < pattern.length; j++) {
				if (data[i + j] != pattern[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}
}
