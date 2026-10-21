package io.mosip.mock.sbi.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WebcamFrameProviderTest {

	@Test
	void cameraThatCannotBeOpenedIsRejectedNamingItsIndex() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> FrameProviders.open(FaceStreamSource.parse("webcam:57")));

		assertEquals("Camera 57 could not be opened", error.getMessage());
	}
}
