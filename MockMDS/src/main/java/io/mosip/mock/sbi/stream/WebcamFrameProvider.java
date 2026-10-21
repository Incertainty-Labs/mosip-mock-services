package io.mosip.mock.sbi.stream;

import java.io.IOException;
import java.util.Locale;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

/**
 * Reads a webcam through OpenCV, which MockMDS already ships through biometrics-util (no extra native library).
 * Frames are asked for at 640x480 and returned as JPEG.
 */
final class WebcamFrameProvider implements FrameProvider {

	static {
		nu.pattern.OpenCV.loadLocally();
	}

	private static final int WIDTH = 640;
	private static final int HEIGHT = 480;

	private final int index;
	private final VideoCapture camera;
	private final Mat frame = new Mat();

	private WebcamFrameProvider(int index, VideoCapture camera) {
		this.index = index;
		this.camera = camera;
	}

	/**
	 * @param index the camera index
	 * @return the open camera
	 * @throws IllegalArgumentException if the camera cannot be opened
	 */
	static WebcamFrameProvider open(int index) {
		VideoCapture camera = openCamera(index);
		if (camera == null) {
			throw new IllegalArgumentException("Camera " + index + " could not be opened");
		}
		camera.set(Videoio.CAP_PROP_FRAME_WIDTH, WIDTH);
		camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, HEIGHT);
		return new WebcamFrameProvider(index, camera);
	}

	/**
	 * On Windows, DirectShow first: OpenCV's default Media Foundation backend took 49 s to open a laptop webcam
	 * (Lenovo FHD Webcam), which stalls the STREAM response.
	 */
	private static VideoCapture openCamera(int index) {
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
		int[] backends = windows ? new int[] { Videoio.CAP_DSHOW, Videoio.CAP_ANY } : new int[] { Videoio.CAP_ANY };
		for (int backend : backends) {
			VideoCapture camera = new VideoCapture(index, backend);
			if (camera.isOpened()) {
				return camera;
			}
			camera.release();
		}
		return null;
	}

	@Override
	public synchronized byte[] nextFrame() throws IOException {
		if (!camera.read(frame) || frame.empty()) {
			throw new IOException("Camera " + index + " gave no frame");
		}
		MatOfByte jpeg = new MatOfByte();
		if (!Imgcodecs.imencode(".jpg", frame, jpeg)) {
			throw new IOException("Camera " + index + " frame could not be encoded as JPEG");
		}
		return jpeg.toArray();
	}

	@Override
	public synchronized void close() {
		camera.release();
		frame.release();
	}
}
