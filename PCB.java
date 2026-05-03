package project;

public class PCB {

	int processID;
	int burstTime;
	int remainingBurst; // used for RR
	int priority;
	int memoryRequired;
	int startTime;
	int terminationTime;
	int waitingTime;
	int turnaroundTime;
	String state; // "new", "ready", "running", "terminated"
	int timeInReadyQueue; // for starvation detection
	int arrivalOrder; // order of arrival (from file)

	public PCB(int processID, int burstTime, int priority, int memoryRequired, int arrivalOrder) {
		this.processID = processID;
		this.burstTime = burstTime;
		this.remainingBurst = burstTime;
		this.priority = priority;
		this.memoryRequired = memoryRequired;
		this.state = "new";
		this.startTime = -1;
		this.terminationTime = -1;
		this.waitingTime = 0;
		this.turnaroundTime = 0;
		this.timeInReadyQueue = 0;
		this.arrivalOrder = arrivalOrder;
	}

	public String toString() {
		return "P" + processID;
	}
}
