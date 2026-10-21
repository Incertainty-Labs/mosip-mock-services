package io.mosip.mock.sbi.stream;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Where the face device's live stream comes from, read from {@code mosip.mock.sbi.face.stream.source}:
 * {@code static} (the bundled image, the default), {@code webcam} or {@code webcam:<n>} (camera index), or
 * {@code file:<path>} (an MJPEG file or a folder of JPEG images, played in a loop).
 */
public final class FaceStreamSource {

	/** The kind of frame source. */
	public enum Kind {
		STATIC, WEBCAM, FILE
	}

	private static final String WEBCAM = "webcam";
	private static final String WEBCAM_PREFIX = "webcam:";
	private static final String FILE_PREFIX = "file:";

	private final Kind kind;
	private final int cameraIndex;
	private final Path path;

	private FaceStreamSource(Kind kind, int cameraIndex, Path path) {
		this.kind = kind;
		this.cameraIndex = cameraIndex;
		this.path = path;
	}

	/**
	 * @param value the configured value; {@code null} or blank means {@code static}
	 * @return the parsed source
	 * @throws IllegalArgumentException if the value is not one of the documented forms
	 */
	public static FaceStreamSource parse(String value) {
		String trimmed = value == null ? "" : value.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.isEmpty() || lower.equals("static")) {
			return new FaceStreamSource(Kind.STATIC, -1, null);
		}
		if (lower.equals(WEBCAM)) {
			return new FaceStreamSource(Kind.WEBCAM, 0, null);
		}
		if (lower.startsWith(WEBCAM_PREFIX)) {
			String index = trimmed.substring(WEBCAM_PREFIX.length());
			if (index.matches("\\d+")) {
				return new FaceStreamSource(Kind.WEBCAM, Integer.parseInt(index), null);
			}
		}
		if (lower.startsWith(FILE_PREFIX)) {
			String file = trimmed.substring(FILE_PREFIX.length()).trim();
			if (!file.isEmpty()) {
				return new FaceStreamSource(Kind.FILE, -1, Paths.get(file));
			}
		}
		throw new IllegalArgumentException("mosip.mock.sbi.face.stream.source: expected static, webcam, webcam:<n> or "
				+ "file:<path>, got '" + value + "'");
	}

	/**
	 * @param folder the folder a relative {@code file:} path is relative to
	 * @return this source, with a relative file path resolved against {@code folder}
	 */
	public FaceStreamSource resolveAgainst(Path folder) {
		if (kind != Kind.FILE || path.isAbsolute()) {
			return this;
		}
		return new FaceStreamSource(Kind.FILE, -1, folder.resolve(path));
	}

	/** @return the kind of source */
	public Kind kind() {
		return kind;
	}

	/** @return the camera index for {@link Kind#WEBCAM}, otherwise -1 */
	public int cameraIndex() {
		return cameraIndex;
	}

	/** @return the MJPEG file or JPEG folder for {@link Kind#FILE}, otherwise {@code null} */
	public Path path() {
		return path;
	}
}
