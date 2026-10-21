package io.mosip.mock.sbi.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The face stream source and whether RCAPTURE returns the last streamed frame. A profile folder can override both
 * with a {@code stream.properties} file holding the same keys as {@code application.properties}; a relative
 * {@code file:} path in it is resolved against the profile folder.
 */
public final class FaceStreamSettings {

	/** Live stream source: static (default), webcam, webcam:&lt;n&gt; or file:&lt;path&gt;. */
	public static final String SOURCE_KEY = "mosip.mock.sbi.face.stream.source";
	/** Whether RCAPTURE returns the last streamed frame; unset means on for every source except static. */
	public static final String CAPTURE_FROM_STREAM_KEY = "mosip.mock.sbi.face.capture.from.stream";
	/** The per-profile settings file. */
	public static final String PROFILE_FILE = "stream.properties";

	private final FaceStreamSource source;
	private final boolean captureFromStream;

	/**
	 * @param source            where the stream takes its frames from
	 * @param captureFromStream whether RCAPTURE returns the last streamed frame
	 */
	public FaceStreamSettings(FaceStreamSource source, boolean captureFromStream) {
		this.source = source;
		this.captureFromStream = captureFromStream;
	}

	/**
	 * @param sourceValue  the configured source; {@code null} or blank means static
	 * @param captureValue the configured capture setting; {@code null} or blank means "follow the source"
	 * @return the settings
	 * @throws IllegalArgumentException if the source is not one of the documented forms
	 */
	public static FaceStreamSettings fromConfig(String sourceValue, String captureValue) {
		FaceStreamSource source = FaceStreamSource.parse(sourceValue);
		return new FaceStreamSettings(source, captureFromStream(captureValue, source));
	}

	/**
	 * @param profileFolder the active profile's folder
	 * @param fallback      the settings from {@code application.properties}
	 * @return the profile's settings, or {@code fallback} when it has no {@value #PROFILE_FILE}
	 * @throws IllegalArgumentException if the profile's source is invalid; the message starts with the file path
	 */
	public static FaceStreamSettings forProfile(Path profileFolder, FaceStreamSettings fallback) {
		Path file = profileFolder.resolve(PROFILE_FILE);
		if (!Files.isRegularFile(file)) {
			return fallback;
		}
		Properties properties = new Properties();
		try (InputStream in = Files.newInputStream(file)) {
			properties.load(in);
		} catch (IOException ex) {
			throw new UncheckedIOException("Cannot read " + file, ex);
		}
		String sourceValue = properties.getProperty(SOURCE_KEY);
		FaceStreamSource source;
		try {
			source = sourceValue == null || sourceValue.isBlank() ? fallback.source()
					: FaceStreamSource.parse(sourceValue).resolveAgainst(profileFolder);
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException(file + ": " + ex.getMessage(), ex);
		}
		String captureValue = properties.getProperty(CAPTURE_FROM_STREAM_KEY);
		boolean capture = (captureValue == null || captureValue.isBlank()) && source == fallback.source()
				? fallback.captureFromStream()
				: captureFromStream(captureValue, source);
		return new FaceStreamSettings(source, capture);
	}

	private static boolean captureFromStream(String value, FaceStreamSource source) {
		if (value == null || value.isBlank()) {
			return source.kind() != FaceStreamSource.Kind.STATIC;
		}
		return Boolean.parseBoolean(value.trim());
	}

	/** @return where the stream takes its frames from */
	public FaceStreamSource source() {
		return source;
	}

	/** @return whether RCAPTURE returns the last streamed frame */
	public boolean captureFromStream() {
		return captureFromStream;
	}
}
