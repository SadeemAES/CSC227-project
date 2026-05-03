package project;

public class GanttEntry {

	public int processID;
	public int startTime;
	public int endTime;
	public int burstAtStart;
	public int burstAtEnd;

	public GanttEntry(int processID, int startTime, int endTime, int burstAtStart, int burstAtEnd) {
		this.processID = processID;
		this.startTime = startTime;
		this.endTime = endTime;
		this.burstAtStart = burstAtStart;
		this.burstAtEnd = burstAtEnd;
	}
}
