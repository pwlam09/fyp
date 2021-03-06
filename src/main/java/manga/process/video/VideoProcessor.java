package manga.process.video;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import manga.detect.SpeakerDetector;
import manga.process.subtitle.Subtitle;
import manga.process.subtitle.SubtitleProcessor;

/**
 * For subtitle and frame extraction using FFmpeg 
 * (absolute path for now and relative path should be used later)
 * 
 * @author PuiWa
 */
public class VideoProcessor {
	// time base = 1^(-3)s
	private static VideoProcessor instance = new VideoProcessor();
	
	private static ArrayList<KeyFrame> allKeyFrames = new ArrayList<>(); 
	
	private VideoProcessor() {
		
	}
	
	public static void preprocessing(String videoPath) {
		extractKeyFrameInfo(videoPath);
		extractSRT(videoPath);
		// Store key frames for later use
		allKeyFrames = extractKeyFrames(videoPath);
	}
	
	private static boolean extractKeyFrameInfo(String videoPath) {
		File videoSegmentationFile = new File(VideoProcessor.class.getResource("/video_segmentation/video_segmentation.exe").getFile());
		String videoSegmentationFilePath = videoSegmentationFile.getAbsolutePath();
		
		if (!requiredDocsExist(videoPath)) {
			System.out.println("Extracting key frame information...");
			try {
				ProcessBuilder pb = new ProcessBuilder(videoSegmentationFilePath, videoPath);
				pb.redirectOutput(Redirect.INHERIT);
				pb.redirectError(Redirect.PIPE);
				Process p = pb.start();
				p.waitFor();
				// FFmpeg prints result to stderr, redirect to stdout
				BufferedReader err_reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				String err_ine;
				while ((err_ine = err_reader.readLine()) != null) {
					System.out.println(err_ine);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return true;
	}
	
	/**
	 * Check if <video name>_shots.txt is in the same directory of video
	 */
	private static boolean requiredDocsExist(String videoPath) {
		String clipShotsFilePath = null;
		if (videoPath.lastIndexOf(".") != -1) {
			clipShotsFilePath = videoPath.substring(0, videoPath.lastIndexOf("."))+"_shots.txt";
		}
		File videoShotFile = new File(clipShotsFilePath);
		if (videoShotFile.exists()) {
			System.out.println("Key frame information exists!");
			return true;
		} else {
			System.out.println("Key frame information not available!");
			return false;
		}
	}

	/**
	 * Extract 1st subtitle stream (SRT format) from mp4 video
	 */
	private static boolean extractSRT(String videoPath) {
		File ffmpegFile = new File(VideoProcessor.class.getResource("/ffmpeg/ffmpeg.exe").getFile());
		String ffmpegPath = ffmpegFile.getAbsolutePath();
		
		// Build process with command for extracting 1st subtitle stream from video (mp4 format), output file name=sub.srt, auto-overwrite
		try {
			ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-i", videoPath, "-an", "-vn", "-c:s:0", "srt", "-y", "sub.srt");
			pb.redirectOutput(Redirect.INHERIT);
			pb.redirectError(Redirect.PIPE);
			Process p = pb.start();
			p.waitFor();
			// ffmpeg prints result to stderr, redirect to stdout
			BufferedReader err_reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			String err_ine;
			while ((err_ine = err_reader.readLine()) != null) {
				System.out.println(err_ine);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return true;
	}
	
	public static int getKeyFrameCount() {
		return allKeyFrames.size();
	}
	
	private static ArrayList<KeyFrame> extractKeyFrames(String videoPath) {
		File opencvDll = new File(VideoProcessor.class.getResource("/opencv/opencv_java310.dll").getFile());
		String openCVPath = opencvDll.getAbsolutePath();
		System.load(openCVPath);
		
		ArrayList<KeyFrame> allFrameImgs = new ArrayList<>();
		VideoCapture vid = new VideoCapture(videoPath);
		File clipShots = new File(getVideoShotFilePath(videoPath));
		FileReader fr = null;
		try {
			fr = new FileReader(clipShots);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		BufferedReader br = new BufferedReader(fr);
		String line = "";
		try {
			while ((line = br.readLine()) != null) {
				String[] frameNumbers = line.split(" ");
				vid.set(Videoio.CAP_PROP_POS_FRAMES, Double.parseDouble(frameNumbers[2]));
				Mat opencvImg = new Mat();
				if (!vid.isOpened()) {
					System.out.println("error open video");
				} else {
					vid.read(opencvImg);
					if (opencvImg != null) {
						double cShotTimestamp = vid.get(Videoio.CAP_PROP_POS_MSEC);
						vid.set(Videoio.CAP_PROP_POS_FRAMES, Double.parseDouble(frameNumbers[0]));
						double sShotTimestamp = vid.get(Videoio.CAP_PROP_POS_MSEC);
						vid.set(Videoio.CAP_PROP_POS_FRAMES, Double.parseDouble(frameNumbers[1]));
						double eShotTimestamp = vid.get(Videoio.CAP_PROP_POS_MSEC);
						allFrameImgs.add(new KeyFrame(opencvImg, cShotTimestamp, sShotTimestamp, eShotTimestamp));
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		vid.release();
		return allFrameImgs;
	}
	
	private static String getVideoShotFilePath(String videoPath) {
		String filepath = videoPath.substring(0, videoPath.lastIndexOf("."))+"_shots.txt";
		return filepath;
	}

	public static ArrayList<KeyFrame> getKeyFrames() {
		return allKeyFrames;
	}
	
	/**
	 * Not using. Now Assume only the biggest detected face in key frame image
	 * as speaker.
	 */
	public static void detectAndSetSubtitleSpeakers(String videoPath) {
		File opencvDll = new File(VideoProcessor.class.getResource("/opencv/opencv_java310.dll").getFile());
		String openCVPath = opencvDll.getAbsolutePath();
		System.load(openCVPath);
		
		ArrayList<Subtitle> allSubtitles = SubtitleProcessor.getAllRawSubtitles();
		VideoCapture vid = new VideoCapture(videoPath);
		Mat opencvImg = new Mat();
		if (!vid.isOpened()) {
			System.out.println("error open video");
		} else {
			for (Subtitle subtitle:allSubtitles) {
				ArrayList<Mat> interFrames = new ArrayList<>();
				vid.set(Videoio.CAP_PROP_POS_MSEC, subtitle.geteTime());
				double eFrame = vid.get(Videoio.CAP_PROP_POS_FRAMES);
				vid.set(Videoio.CAP_PROP_POS_MSEC, subtitle.getsTime());
				double sFrame = vid.get(Videoio.CAP_PROP_POS_FRAMES);
				double frameInterval = (eFrame-sFrame) / 10;
				System.out.println("CAP_PROP_POS_MSEC: "+vid.get(Videoio.CAP_PROP_POS_MSEC));
				while (vid.get(Videoio.CAP_PROP_POS_FRAMES)<=eFrame) {
					vid.read(opencvImg);
					// Subtitle end time may exceed end of video
					if (opencvImg.empty()) {
						break;
					}
					interFrames.add(opencvImg.clone());
					vid.set(Videoio.CAP_PROP_POS_FRAMES, vid.get(Videoio.CAP_PROP_POS_FRAMES)+frameInterval);
				}
				System.out.printf("Detecting speaker face ... %d%%\n", 
						(int) Math.floor(vid.get(Videoio.CAP_PROP_POS_FRAMES ) / vid.get(Videoio.CAP_PROP_FRAME_COUNT) * 100));
				subtitle.setSpeaker(SpeakerDetector.detectSpeaker(interFrames));
				if (subtitle.getSpeaker() != null) {
					System.out.println("speaker detected");
				} else {
					System.out.println("no speaker");
				}
			}
		}
		vid.release();
	}
}
