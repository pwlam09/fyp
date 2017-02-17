package manga.process.subtitle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import manga.process.video.VideoProcessor;

/**
 * @author PuiWa
 * 
 */
public class SubtitleProcessor {
	private static ArrayList<Subtitle> allSubTextList;
	private static int subTextCounter = 0;
	private static SubtitleProcessor instance = new SubtitleProcessor();

	/**
	 * group 1:		subtitle numeric counter, 
	 * group 2-5:	start time, 
	 * group 6-9:	end time, 
	 * group 11:	subtitle text
	 */
	private static final String nl = "\\\n";
	private static final String sp = "[ \\t]*";
	private static Pattern r = Pattern.compile("(?s)(\\d+)" 
			+ sp + nl + "(\\d{1,2}):(\\d\\d):(\\d\\d),(\\d\\d\\d)"
			+ sp + "-->"
			+ sp + "(\\d\\d):(\\d\\d):(\\d\\d),(\\d\\d\\d)" 
			+ sp + "(X1:\\d.*?)??" + nl + "(.*?)" + nl + nl);
	
	private SubtitleProcessor() {
		if (allSubTextList == null) {
			allSubTextList = new ArrayList<>();
		}
	}
	
	public SubtitleProcessor getInstance() {
		if (instance == null) {
			instance = new SubtitleProcessor();
		}
		return instance;
	}
	
	public static void parseSRT() {
		File srt = new File("./sub.srt");
		FileReader fr = null;
		try {
			fr = new FileReader(srt);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		BufferedReader br = new BufferedReader(fr);
		int count = 0;
		String line = "";
		String subtitleStr = "";
		try {
			while ((line = br.readLine()) != null) {
				count++;
				subtitleStr = subtitleStr+line+"\n";
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Matcher m = r.matcher(subtitleStr);
		while (m.find( )) {
			// trim subtitle text style e.g. text contains "<....>"
			long sTimestamp = convertTimeToTimestamp(m.group(2), m.group(3), m.group(4), m.group(5));
			long eTimestamp = convertTimeToTimestamp(m.group(6), m.group(7), m.group(8), m.group(9));
//			System.out.println("sTimestamp: "+sTimestamp);
			// subtitle may contain line break
			// subtitle may contain hyphen which indicates speaker (delete?)
			String subText = m.group(11).replaceAll("\\r\\n|\\r|\\n", " ").replaceAll("- ", "");	
			// trim style tags of extracted text if any
			if (subText.contains("<") && subText.contains(">")) {
				subText = getSubtitleTextWithoutStyle(subText);
			}
			allSubTextList.add(new Subtitle(sTimestamp, eTimestamp, subText));
		}
	}
	
	private static long convertTimeToTimestamp(String hour, String minute, String sec, String millisec) {
		long timestamp = (long) ((
				Integer.parseInt(hour) * 60 * 60 + 
				Integer.parseInt(minute) * 60 + 
				Integer.parseInt(sec) + 
				Integer.parseInt(millisec) / 1000.0) * 1000L);
//		System.out.println("timestamp: "+timestamp);
		return timestamp;
	}
	
	private static String getSubtitleTextWithoutStyle(String styledText) {
		String subText = "";
		String[] splitedTextList = styledText.split(">");
		for (int i = 0; i < splitedTextList.length; i++) {
			if (splitedTextList[i].charAt(0) != '<') {
				subText = subText + splitedTextList[i].substring(0, splitedTextList[i].indexOf("<")) + " ";
			}
		}
//		System.out.println("subText: "+subText);
		return subText;
	}

	/**
	 * This method returns a list of subtitle text between two keyframes.
	 * The timestamp of the two keyframes will be used to retrieve the 
	 * subtitle within the two timestamps.
	 * 
	 * @param timestamp1 timestamp of a keyframe
	 * @param timestamp2 timestamp of another keyframe
	 * @return a list of subtitle text between two keyframes
	 */
	public static ArrayList<Subtitle> getSubTextList(long timestamp1, long timestamp2) {
		ArrayList<Subtitle> subTextList = new ArrayList<>();
		long sShotTimestamp = VideoProcessor.getsShotTimestamp(timestamp1);
		long eShotTimestamp = VideoProcessor.getsShotTimestamp(timestamp2);
		for (Subtitle sub : allSubTextList) {
			long subtitleStartTime = sub.getsTime();
			long subtitleEndTime = sub.geteTime();
			
//			boolean isAfterCurrFrame = (sShotTimestamp <= subtitleStartTime) && (subtitleStartTime < eShotTimestamp) &&
//					(Math.abs(subtitleStartTime-eShotTimestamp) >= Math.abs(subtitleEndTime-eShotTimestamp));
//			boolean isBeforeCurrFrame = (sShotTimestamp < subtitleEndTime) && (subtitleEndTime <= eShotTimestamp) &&
//					(Math.abs(subtitleEndTime-sShotTimestamp) > Math.abs(subtitleStartTime-sShotTimestamp));
//			if (isAfterCurrFrame || isBeforeCurrFrame) {
//				subTextList.add(sub);
//			}
			
			if (subtitleStartTime >= sShotTimestamp && subtitleStartTime < eShotTimestamp) {
//				System.out.println("entry.getValue():"+entry.getValue());
				subTextList.add(sub);
			}
		}
//		System.out.println("sShot Time: "+timestampToTimeString(sShotTimestamp));
//		System.out.println("eShot Time: "+timestampToTimeString(eShotTimestamp));
		return subTextList;
	}
	
	/**
	 * For testing.
	 */
	public static void printSubText() {
		for (Subtitle sub : allSubTextList) {
//			VideoProcessor.printFrameSubtitleTimeMatch(entry.getKey().getsTime(), entry.getKey().geteTime());
			System.out.println(sub);
		}
	}
	
	/**
	 * For testing
	 * @param timestamp
	 * @return
	 */
	public static String timestampToTimeString(long timestamp) {
		String colon = ":";
		int h = (int) (((timestamp / 1000) / 60) / 60);
		int m = (int) (timestamp / (1000 * 60) % 60);
		int s = (int) (timestamp / 1000 % 60);
		return (h+colon+m+colon+s);
	}

	public static ArrayList<Subtitle> getAllSubTextList() {
		return allSubTextList;
	}
}
