package io.mosip.mock.sbi.stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FaceStreamSourceTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "static", " STATIC ", "  " })
	void unsetOrStaticKeepsTheBundledImage(String value) {
		assertEquals(FaceStreamSource.Kind.STATIC, FaceStreamSource.parse(value).kind());
	}

	@Test
	void webcamWithoutIndexIsTheFirstCamera() {
		FaceStreamSource source = FaceStreamSource.parse("webcam");

		assertAll(() -> assertEquals(FaceStreamSource.Kind.WEBCAM, source.kind()),
				() -> assertEquals(0, source.cameraIndex()));
	}

	@Test
	void webcamWithIndexSelectsThatCamera() {
		FaceStreamSource source = FaceStreamSource.parse("webcam:2");

		assertAll(() -> assertEquals(FaceStreamSource.Kind.WEBCAM, source.kind()),
				() -> assertEquals(2, source.cameraIndex()));
	}

	@Test
	void fileKeepsThePathAfterThePrefix() {
		FaceStreamSource source = FaceStreamSource.parse("file:C:/clips/bonafide 1.mjpeg");

		assertAll(() -> assertEquals(FaceStreamSource.Kind.FILE, source.kind()),
				() -> assertEquals(Paths.get("C:/clips/bonafide 1.mjpeg"), source.path()));
	}

	@ParameterizedTest
	@ValueSource(strings = { "webcam:", "webcam:-1", "webcam:front", "file:", "file:  ", "camera", "rtsp://x" })
	void anythingElseIsRejectedWithTheValueInTheMessage(String value) {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> FaceStreamSource.parse(value));

		assertEquals("mosip.mock.sbi.face.stream.source: expected static, webcam, webcam:<n> or file:<path>, got '"
				+ value + "'", error.getMessage());
	}
}
