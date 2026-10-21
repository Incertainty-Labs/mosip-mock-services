package io.mosip.mock.sbi.devicehelper.face;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.mosip.mock.sbi.SBIConstant;
import io.mosip.mock.sbi.stream.FaceStreamSource;
import io.mosip.mock.sbi.stream.FramePacer;
import io.mosip.mock.sbi.util.ApplicationPropertyHelper;
import io.mosip.mock.sbi.util.FileHelper;

class SBIFaceHelperStreamTest {

	@TempDir
	Path dir;

	private final SBIFaceHelper helper = SBIFaceHelper.getInstance(4501, SBIConstant.PURPOSE_REGISTRATION, "keys",
			"JP2000");

	@Test
	void fileSourceStreamsItsFramesInOrder() throws IOException {
		byte[] first = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, (byte) 0xFF, (byte) 0xD9 };
		byte[] second = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 2, (byte) 0xFF, (byte) 0xD9 };
		Files.write(dir.resolve("a.jpg"), first);
		Files.write(dir.resolve("b.jpg"), second);
		helper.setStreamSource(FaceStreamSource.parse("file:" + dir));
		helper.setDeviceSubId(SBIConstant.DEVICE_FACE_SUB_TYPE_ID_FULLFACE);
		helper.initDevice();

		assertEquals(0, helper.getLiveStream());
		byte[] shownFirst = helper.getCaptureInfo().getImage();
		assertEquals(0, helper.getLiveStream());
		byte[] shownSecond = helper.getCaptureInfo().getImage();

		assertAll(() -> assertArrayEquals(first, shownFirst), () -> assertArrayEquals(second, shownSecond),
				() -> assertArrayEquals(second, helper.getLastStreamedFrame()));
	}

	@Test
	void staticSourceStreamsTheBundledImage() throws Exception {
		helper.setStreamSource(FaceStreamSource.parse("static"));
		helper.setDeviceSubId(SBIConstant.DEVICE_FACE_SUB_TYPE_ID_FULLFACE);
		helper.initDevice();

		assertEquals(0, helper.getLiveStream());

		byte[] bundled = Files.readAllBytes(Path.of(FileHelper.getCanonicalPath()
				+ ApplicationPropertyHelper.getPropertyKeyValue(SBIConstant.MOSIP_STREAM_FACE_SUBID_FULLFACE)));
		assertArrayEquals(bundled, helper.getCaptureInfo().getImage());
	}

	@Test
	void unreadableSourceEndsTheStreamInsteadOfFallingBackToStatic() {
		helper.setStreamSource(FaceStreamSource.parse("file:" + dir.resolve("missing.mjpeg")));
		helper.setDeviceSubId(SBIConstant.DEVICE_FACE_SUB_TYPE_ID_FULLFACE);
		helper.initDevice();

		assertAll(() -> assertEquals(-1, helper.getLiveStream()), () -> assertNull(helper.getLastStreamedFrame()));
	}

	@Test
	void lastStreamedFrameOutlivesTheStream() throws IOException {
		byte[] frame = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 7, (byte) 0xFF, (byte) 0xD9 };
		Files.write(dir.resolve("only.jpg"), frame);
		helper.setStreamSource(FaceStreamSource.parse("file:" + dir));
		helper.setDeviceSubId(SBIConstant.DEVICE_FACE_SUB_TYPE_ID_FULLFACE);
		helper.initDevice();
		helper.getLiveStream();

		helper.deInitDevice();

		assertArrayEquals(frame, helper.getLastStreamedFrame());
	}

	@Test
	void streamIsPacedAtTheConfiguredFrameRateFromWhenTheDeviceStarts() {
		helper.setStreamPacer(FramePacer.fromConfig("25"));
		helper.setStreamSource(FaceStreamSource.parse("static"));
		long before = System.currentTimeMillis();
		helper.initDevice();

		long wait = helper.streamWaitMillis(before);

		// the first frame is due 40 ms after initDevice, which ran at or after "before"
		assertTrue(wait >= 40 && wait <= 40 + (System.currentTimeMillis() - before), "wait was " + wait);
	}
}
