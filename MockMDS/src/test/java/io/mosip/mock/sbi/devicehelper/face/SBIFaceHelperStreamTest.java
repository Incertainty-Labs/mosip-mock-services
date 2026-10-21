package io.mosip.mock.sbi.devicehelper.face;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import javax.imageio.ImageIO;

import io.mosip.biometrics.util.ConvertRequestDto;
import io.mosip.biometrics.util.face.FaceDecoder;

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
	void captureFromStreamEncodesTheLastStreamedFrameAsTheIsoFace() throws Exception {
		Files.write(dir.resolve("frame.jpg"), jpeg(64, 48, Color.RED));
		helper.setStreamSource(FaceStreamSource.parse("file:" + dir));
		helper.setCaptureFromStream(true);
		startStreamAndTakeOneFrame();

		helper.getBioCapture(false);

		BufferedImage face = decodeIsoFace(((SBIFaceCaptureInfo) helper.getCaptureInfo()).getBioValueFace());
		Color centre = new Color(face.getRGB(32, 24));
		assertAll(() -> assertEquals(64, face.getWidth()), () -> assertEquals(48, face.getHeight()),
				() -> assertTrue(centre.getRed() > 200 && centre.getGreen() < 60 && centre.getBlue() < 60,
						"centre pixel was " + centre));
	}

	@Test
	void withoutCaptureFromStreamTheProfileIsoIsReturned() throws Exception {
		Files.write(dir.resolve("frame.jpg"), jpeg(64, 48, Color.RED));
		helper.setStreamSource(FaceStreamSource.parse("file:" + dir));
		helper.setCaptureFromStream(false);
		startStreamAndTakeOneFrame();

		helper.getBioCapture(false);

		byte[] profileIso = Files.readAllBytes(Path.of(FileHelper.getCanonicalPath(), "Profile",
				SBIConstant.PROFILE_DEFAULT, SBIConstant.PURPOSE_REGISTRATION, SBIConstant.PROFILE_BIO_FILE_NAME_FACE));
		assertArrayEquals(profileIso,
				Base64.getUrlDecoder().decode(((SBIFaceCaptureInfo) helper.getCaptureInfo()).getBioValueFace()));
	}

	@Test
	void captureFromStreamIsOnByDefaultOnlyForNonStaticSources() {
		assertAll(() -> assertTrue(SBIFaceHelper.captureFromStreamFromConfig(null, FaceStreamSource.parse("webcam"))),
				() -> assertFalse(SBIFaceHelper.captureFromStreamFromConfig(null, FaceStreamSource.parse("static"))),
				() -> assertFalse(SBIFaceHelper.captureFromStreamFromConfig("false", FaceStreamSource.parse("webcam"))),
				() -> assertTrue(SBIFaceHelper.captureFromStreamFromConfig(" TRUE ", FaceStreamSource.parse("static"))));
	}

	private void startStreamAndTakeOneFrame() {
		helper.setProfileId(SBIConstant.PROFILE_DEFAULT);
		helper.setDeviceSubId(SBIConstant.DEVICE_FACE_SUB_TYPE_ID_FULLFACE);
		helper.initDevice();
		assertEquals(0, helper.getLiveStream());
		helper.deInitDevice();
		// RCAPTURE starts the device again, as SBIServiceResponse does
		helper.initDevice();
	}

	private static BufferedImage decodeIsoFace(String bioValue) throws Exception {
		ConvertRequestDto request = new ConvertRequestDto();
		request.setModality("Face");
		request.setVersion("ISO19794_5_2011");
		request.setInputBytes(Base64.getUrlDecoder().decode(bioValue));
		return FaceDecoder.convertFaceISOToBufferedImage(request);
	}

	private static byte[] jpeg(int width, int height, Color colour) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(colour);
		g.fillRect(0, 0, width, height);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
		return out.toByteArray();
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
