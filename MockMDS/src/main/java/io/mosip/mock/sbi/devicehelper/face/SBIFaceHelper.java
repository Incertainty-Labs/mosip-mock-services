package io.mosip.mock.sbi.devicehelper.face;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mosip.biometrics.util.CommonUtil;
import io.mosip.mock.sbi.SBIConstant;
import io.mosip.mock.sbi.devicehelper.SBIDeviceHelper;
import io.mosip.mock.sbi.stream.FaceStreamSettings;
import io.mosip.mock.sbi.stream.FaceStreamSource;
import io.mosip.mock.sbi.stream.FramePacer;
import io.mosip.mock.sbi.stream.FrameProvider;
import io.mosip.mock.sbi.stream.FrameProviders;
import io.mosip.mock.sbi.util.ApplicationPropertyHelper;
import io.mosip.mock.sbi.util.BioUtilHelper;
import io.mosip.mock.sbi.util.FileHelper;
import io.mosip.mock.sbi.util.StringHelper;

public class SBIFaceHelper extends SBIDeviceHelper {
	private static final Logger logger = LoggerFactory.getLogger(SBIFaceHelper.class);

	/** Live stream frame rate, 1 to 60 (default 20). */
	public static final String STREAM_FPS_KEY = "mosip.mock.sbi.face.stream.fps";

	/** OpenCV JPEG 2000 compression x1000; 1000 is lossless. */
	private static final int JP2_LOSSLESS = 1000;

	/** Settings from application.properties; a profile's stream.properties overrides them. */
	private FaceStreamSettings configured;
	private FramePacer streamPacer;
	private Path profileRoot;
	/** Settings of the running stream; {@code null} when the profile's settings are invalid. */
	private FaceStreamSettings active;
	private FrameProvider frameProvider;
	private volatile byte[] lastStreamedFrame;

	private SBIFaceHelper(int port, String purpose, String keystoreFilePath, String biometricImageType) {
		super(port, purpose, SBIConstant.MOSIP_BIOMETRIC_TYPE_FACE, SBIConstant.MOSIP_BIOMETRIC_SUBTYPE_FACE,
				keystoreFilePath, biometricImageType);
		this.configured = configuredSettings();
		this.streamPacer = FramePacer.fromConfig(ApplicationPropertyHelper.getPropertyKeyValue(STREAM_FPS_KEY));
		this.profileRoot = configuredProfileRoot();
	}

	// synchronized method to control simultaneous access
	public static synchronized SBIFaceHelper getInstance(int port, String purpose, String keystoreFilePath,
			String biometricImageType) {
		return new SBIFaceHelper(port, purpose, keystoreFilePath, biometricImageType);
	}

	private static FaceStreamSettings configuredSettings() {
		String source = ApplicationPropertyHelper.getPropertyKeyValue(FaceStreamSettings.SOURCE_KEY);
		String capture = ApplicationPropertyHelper.getPropertyKeyValue(FaceStreamSettings.CAPTURE_FROM_STREAM_KEY);
		try {
			return FaceStreamSettings.fromConfig(source, capture);
		} catch (IllegalArgumentException ex) {
			logger.error("{}; streaming the static image", ex.getMessage());
			return FaceStreamSettings.fromConfig("static", capture);
		}
	}

	private static Path configuredProfileRoot() {
		try {
			return Paths.get(FileHelper.getCanonicalPath()
					+ ApplicationPropertyHelper.getPropertyKeyValue(SBIConstant.MOSIP_PROFILE_FOLDER_PATH));
		} catch (IOException | RuntimeException ex) {
			logger.error("Cannot find the profile folder; profile stream settings are ignored", ex);
			return null;
		}
	}

	/** @param source where the stream takes its frames from when the profile does not say */
	public void setStreamSource(FaceStreamSource source) {
		this.configured = new FaceStreamSettings(source, configured.captureFromStream());
	}

	/** @param captureFromStream whether RCAPTURE returns the last streamed frame when the profile does not say */
	public void setCaptureFromStream(boolean captureFromStream) {
		this.configured = new FaceStreamSettings(configured.source(), captureFromStream);
	}

	/** @param profileRoot the folder holding one folder per profile */
	public void setProfileRoot(Path profileRoot) {
		this.profileRoot = profileRoot;
	}

	/** @param pacer the frame rate for the live stream */
	public void setStreamPacer(FramePacer pacer) {
		this.streamPacer = pacer;
	}

	/** @return the last frame sent on the live stream, kept after the stream ends; {@code null} before any */
	public byte[] getLastStreamedFrame() {
		return lastStreamedFrame;
	}

	/** @return the settings for the active profile, or {@code null} when its stream.properties is invalid */
	private FaceStreamSettings settingsForActiveProfile() {
		if (profileRoot == null || getProfileId() == null || getProfileId().isBlank()) {
			return configured;
		}
		try {
			return FaceStreamSettings.forProfile(profileRoot.resolve(getProfileId()), configured);
		} catch (RuntimeException ex) {
			logger.error("Profile {} face stream settings: {}", getProfileId(), ex.getMessage());
			return null;
		}
	}

	/**
	 * @return the last streamed frame as an ISO 19794-5 face (lossless JPEG 2000), or {@code null} when nothing was
	 *         streamed or encoding failed, so the profile ISO is used
	 */
	private byte[] streamedFaceIso() {
		byte[] frame = lastStreamedFrame;
		if (frame == null) {
			logger.warn("Capture from stream is on but nothing was streamed; returning the profile face");
			return null;
		}
		try {
			byte[] jp2 = CommonUtil.convertJPEGToJP2UsingOpenCV(frame, JP2_LOSSLESS);
			return BioUtilHelper.getFaceIsoFromJP2000(getPurpose(), SBIConstant.MOSIP_BIOMETRIC_SUBTYPE_FACE, jp2);
		} catch (Exception ex) {
			logger.error("Encoding the streamed frame as an ISO face failed; returning the profile face", ex);
			return null;
		}
	}

	@Override
	public long streamWaitMillis(long nowMs) {
		return streamPacer.waitMillis(nowMs);
	}

	@Override
	public long initDevice() {
		SBIFaceCaptureInfo captureInfo = new SBIFaceCaptureInfo();
		captureInfo.initCaptureInfo();
		setCaptureInfo(captureInfo);
		active = settingsForActiveProfile();
		openFrameProvider();
		streamPacer.start(System.currentTimeMillis());
		return 0;
	}

	private void openFrameProvider() {
		closeFrameProvider();
		if (active == null || active.source().kind() == FaceStreamSource.Kind.STATIC) {
			return;
		}
		try {
			frameProvider = FrameProviders.open(active.source());
		} catch (IOException | RuntimeException ex) {
			logger.error("Cannot open the {} face stream source: {}", active.source().kind(), ex.getMessage());
		}
	}

	private void closeFrameProvider() {
		if (frameProvider != null) {
			try {
				frameProvider.close();
			} catch (IOException ex) {
				logger.error("Closing the face stream source failed", ex);
			}
			frameProvider = null;
		}
	}

	@Override
	public int deInitDevice() {
		closeFrameProvider();
		if (getCaptureInfo() != null)
			getCaptureInfo().deInitCaptureInfo();
		setCaptureInfo(null);
		return 0;
	}

	@Override
	public int getLiveStream() {
		byte[] image = nextStreamFrame();
		if (image == null || image.length == 0)
			return -1;
		getCaptureInfo().setImage(image);
		lastStreamedFrame = image;

		return 0;
	}

	/** The next frame from the active source; a source that failed to open ends the stream (no static fallback). */
	private byte[] nextStreamFrame() {
		if (active == null) {
			return new byte[0];
		}
		if (active.source().kind() == FaceStreamSource.Kind.STATIC) {
			return getLiveStreamBufferedImage();
		}
		if (frameProvider == null) {
			return new byte[0];
		}
		try {
			return frameProvider.nextFrame();
		} catch (IOException ex) {
			logger.error("Face stream source failed", ex);
			return new byte[0];
		}
	}

	@Override
	@SuppressWarnings({ "java:S3776" })
	public int getBioCapture(boolean isUsedForAuthenication) throws Exception {
		byte[] isoData = null;

		String seedName = "";
		if (this.getProfileId().equalsIgnoreCase(SBIConstant.PROFILE_AUTOMATIC)) {
			int seedValue = -1;
			if (this.getPurpose().equalsIgnoreCase(SBIConstant.PURPOSE_AUTH) && ApplicationPropertyHelper
					.getPropertyKeyValue(SBIConstant.MOSIP_BIOMETRIC_AUTH_SEED_FACE) != null) {
				seedValue = Integer.parseInt(
						ApplicationPropertyHelper.getPropertyKeyValue(SBIConstant.MOSIP_BIOMETRIC_AUTH_SEED_FACE));
				seedName = String.format("%04d", getRandomNumberForSeed(seedValue)).trim();
			} else if (this.getPurpose().equalsIgnoreCase(SBIConstant.PURPOSE_REGISTRATION) && ApplicationPropertyHelper
					.getPropertyKeyValue(SBIConstant.MOSIP_BIOMETRIC_REGISTRATION_SEED_FACE) != null) {
				seedValue = Integer.parseInt(ApplicationPropertyHelper
						.getPropertyKeyValue(SBIConstant.MOSIP_BIOMETRIC_REGISTRATION_SEED_FACE));
				seedName = String.format("%04d", getRandomNumberForSeed(seedValue)).trim();
			}
		}

		if (!isUsedForAuthenication) {
			// RCAPTURE sets the profile after starting the device, so read the profile's settings here
			FaceStreamSettings settings = settingsForActiveProfile();
			isoData = settings != null && settings.captureFromStream() ? streamedFaceIso() : null;
			if (isoData == null)
				isoData = getBiometricISOImage(seedName, SBIConstant.PROFILE_BIO_FILE_NAME_FACE);
			if (isoData != null && !((SBIFaceCaptureInfo) getCaptureInfo()).isCaptureFace()) {
				((SBIFaceCaptureInfo) getCaptureInfo()).setBioValueFace(StringHelper.base64UrlEncode(isoData));
				if (isScoreFromIso())
					((SBIFaceCaptureInfo) getCaptureInfo())
							.setCaptureScoreFace(BioUtilHelper.getFaceQualityScoreFromIso(getPurpose(), isoData));
				else
					((SBIFaceCaptureInfo) getCaptureInfo()).setCaptureScoreFace(getQualityScore());
			}
			((SBIFaceCaptureInfo) getCaptureInfo()).setCaptureFace(true);

			isoData = getBiometricISOImage(seedName, SBIConstant.PROFILE_BIO_FILE_NAME_FACE_EXCEPTION);
			if (isoData != null && !((SBIFaceCaptureInfo) getCaptureInfo()).isCaptureExceptionPhoto()) {
				((SBIFaceCaptureInfo) getCaptureInfo())
						.setBioValueExceptionPhoto(StringHelper.base64UrlEncode(isoData));
				if (isScoreFromIso())
					((SBIFaceCaptureInfo) getCaptureInfo())
							.setCaptureScoreFace(BioUtilHelper.getFaceQualityScoreFromIso(getPurpose(), isoData));
				else
					((SBIFaceCaptureInfo) getCaptureInfo()).setCaptureScoreFace(getQualityScore());
			}
			((SBIFaceCaptureInfo) getCaptureInfo()).setCaptureExceptionPhoto(true);

			if (((SBIFaceCaptureInfo) getCaptureInfo()).isCaptureFace()
					|| ((SBIFaceCaptureInfo) getCaptureInfo()).isCaptureExceptionPhoto()) {
				getCaptureInfo().setCaptureCompleted(true);
			}
		} else {
			isoData = getBiometricISOImage(seedName, SBIConstant.PROFILE_BIO_FILE_NAME_FACE);
			if (isoData != null && !((SBIFaceCaptureInfo) getCaptureInfo()).isCaptureFace()) {
				getCaptureInfo().addBiometricForBioSubType(SBIConstant.BIO_NAME_UNKNOWN,
						StringHelper.base64UrlEncode(isoData));
				if (isScoreFromIso())
					((SBIFaceCaptureInfo) getCaptureInfo())
							.setCaptureScoreFace(BioUtilHelper.getFaceQualityScoreFromIso(getPurpose(), isoData));
				else
					((SBIFaceCaptureInfo) getCaptureInfo()).setCaptureScoreFace(getQualityScore());
				((SBIFaceCaptureInfo) getCaptureInfo()).setCaptureFace(true);
			}

			if (((SBIFaceCaptureInfo) getCaptureInfo()).isCaptureFace())
				getCaptureInfo().setCaptureCompleted(true);
		}
		return 0;
	}
}