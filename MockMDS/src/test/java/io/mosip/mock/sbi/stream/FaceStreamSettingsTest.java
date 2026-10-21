package io.mosip.mock.sbi.stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FaceStreamSettingsTest {

	@TempDir
	Path profile;

	private final FaceStreamSettings fallback = new FaceStreamSettings(FaceStreamSource.parse("static"), false);

	@Test
	void profileWithoutStreamPropertiesUsesTheFallback() {
		assertSame(fallback, FaceStreamSettings.forProfile(profile, fallback));
	}

	@Test
	void relativeFilePathIsResolvedAgainstTheProfileFolder() throws IOException {
		Files.writeString(profile.resolve("stream.properties"),
				"mosip.mock.sbi.face.stream.source=file:clips/print.mjpeg\n");

		FaceStreamSettings settings = FaceStreamSettings.forProfile(profile, fallback);

		assertAll(() -> assertEquals(FaceStreamSource.Kind.FILE, settings.source().kind()),
				() -> assertEquals(profile.resolve("clips/print.mjpeg"), settings.source().path()),
				() -> assertTrue(settings.captureFromStream(), "capture follows a non-static source"));
	}

	@Test
	void absoluteFilePathIsKept() throws IOException {
		Path clip = profile.resolveSibling("elsewhere").resolve("replay.mjpeg").toAbsolutePath();
		Files.writeString(profile.resolve("stream.properties"),
				"mosip.mock.sbi.face.stream.source=file:" + clip.toString().replace('\\', '/') + "\n");

		assertEquals(clip, FaceStreamSettings.forProfile(profile, fallback).source().path());
	}

	@Test
	void profileCanSetCaptureFromStreamExplicitly() throws IOException {
		Files.writeString(profile.resolve("stream.properties"),
				"mosip.mock.sbi.face.stream.source=static\nmosip.mock.sbi.face.capture.from.stream=true\n");

		FaceStreamSettings settings = FaceStreamSettings.forProfile(profile, fallback);

		assertAll(() -> assertEquals(FaceStreamSource.Kind.STATIC, settings.source().kind()),
				() -> assertTrue(settings.captureFromStream()));
	}

	@Test
	void webcamProfileStreamsTheCameraAndCapturesFromIt() throws IOException {
		Files.writeString(profile.resolve("stream.properties"), "mosip.mock.sbi.face.stream.source=webcam:1\n");

		FaceStreamSettings settings = FaceStreamSettings.forProfile(profile, fallback);

		assertAll(() -> assertEquals(1, settings.source().cameraIndex()),
				() -> assertTrue(settings.captureFromStream()));
	}

	@Test
	void profileWithoutASourceKeepsTheFallbackSourceButMaySetCapture() throws IOException {
		Files.writeString(profile.resolve("stream.properties"), "mosip.mock.sbi.face.capture.from.stream=false\n");
		FaceStreamSettings webcamFallback = new FaceStreamSettings(FaceStreamSource.parse("webcam"), true);

		FaceStreamSettings settings = FaceStreamSettings.forProfile(profile, webcamFallback);

		assertAll(() -> assertEquals(FaceStreamSource.Kind.WEBCAM, settings.source().kind()),
				() -> assertFalse(settings.captureFromStream()));
	}

	@Test
	void invalidSourceInAProfileIsRejectedNamingTheFile() throws IOException {
		Files.writeString(profile.resolve("stream.properties"), "mosip.mock.sbi.face.stream.source=camera\n");

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> FaceStreamSettings.forProfile(profile, fallback));

		assertTrue(error.getMessage().startsWith(profile.resolve("stream.properties") + ": "), error.getMessage());
	}
}
