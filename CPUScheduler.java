package csc227;


import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class CPUScheduler {

    // ─── Shared Data Structures ───────────────────────────────────────────────
    private static final int TOTAL_MEMORY = 2048; // MB
    private static int availableMemory = TOTAL_MEMORY;

    private static final LinkedList<PCB> jobQueue   = new LinkedList<>();
    private static final LinkedList<PCB> readyQueue = new LinkedList<>();

    private static final Object jobQueueLock   = new Object();
    private static final Object readyQueueLock = new Object();
    private static final Object memoryLock     = new Object();

    private static volatile boolean thread1Done = false; // Thread 1 finished loading job queue
    private static volatile boolean allDone     = false; // All processes terminated

    // ─── Main ─────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);
        
        while (true) {

            jobQueue.clear(); // Clears the job queue
            readyQueue.clear(); // Clears the ready queue
            availableMemory = TOTAL_MEMORY;
            thread1Done = false;
            allDone = false;


        System.out.println("________________________________________");
        System.out.println("|                                       |");
        System.out.println("|  Multithreaded CPU Scheduling Sim     |");
        System.out.println("|_______________________________________|");
        System.out.println();
       

        System.out.println();
        System.out.println("Select Scheduling Algorithm:");
        System.out.println("  1. Shortest Job First (SJF)");
        System.out.println("  2. Round Robin (RR, q=5ms)");
        System.out.println("  3. Priority Scheduling (Non-Preemptive)");
        System.out.println("  4. Exit");

        System.out.print("Choice [1/2/3/4]: ");
        int choice = Integer.parseInt(sc.nextLine().trim());

        String algorithm; 
        switch (choice) {
            case 1:  algorithm = "SJF";      break;
            case 2:  algorithm = "RR";       break;
            case 3:  algorithm = "Priority"; break;
            case 4:         System.out.println("Syestem Exit Successfully.. Goodbye ...."); 
                            sc.close();
                            return;

            default: System.out.println("Invalid choice. Defaulting to SJF."); algorithm = "SJF";
        }

        System.out.println("\nRunning " + algorithm + " scheduler...\n");

        final String finalFilePath = "job.txt" ; // Storing the job file name.

        final String finalAlgorithm = algorithm;

        // ── Thread 1: Read file then add it to the Job Queue ──────────────────────────────────
      
        
        Thread thread1 = new Thread(() -> { // Creates Thread 1 to read jobs from job file
            try {
                BufferedReader br = new BufferedReader(new FileReader(finalFilePath));
                String line;
                int order = 0;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    // Format: ID:BurstTime:Priority;Memory
                    String[] parts = line.split(";");
                    String[] left  = parts[0].split(":");
                    int id       = Integer.parseInt(left[0].trim());
                    int burst    = Integer.parseInt(left[1].trim());
                    int priority = Integer.parseInt(left[2].trim());
                    int memory   = Integer.parseInt(parts[1].trim());

                    PCB pcb = new PCB(id, burst, priority, memory, order++); // Creates a new PCB object
                    synchronized (jobQueueLock) { // Locks the job queue before editing it
                        jobQueue.add(pcb);
                        System.out.println("[Thread 1] Process P" + id +
                                " added to Job Queue (Burst=" + burst +
                                "ms, Priority=" + priority +
                                ", Memory=" + memory + "MB)");
                    } // Unlocks the job queue
                }
                br.close();
            } catch (IOException e) {
                System.err.println("[Thread 1] Error reading file: " + e.getMessage());
            }
            thread1Done = true; // Marks that Thread 1 finished loading jobs
            
            System.out.println("[Thread 1] All processes loaded. Thread terminating.");
        }, "Thread-1-FileLoader");

        // ── Thread 2: from Job Queue to Ready Queue (memory check) ─────────────────
        Thread thread2 = new Thread(() -> { // Creates Thread 2 to move jobs from Job Queue to Ready Queue
            while (true) {
               
            	PCB nextJob = null; // Stores the next process to move to Ready Queue

            	synchronized (jobQueueLock) { // Locks Job Queue before accessing it

            	    Iterator<PCB> it = jobQueue.iterator(); 
            	    while (it.hasNext()) {
            	        PCB p = it.next();

            	        synchronized (memoryLock) { // Locks memory before checking available memory

            	            // Checks if enough memory is available for this process
            	            if (availableMemory >= p.memoryRequired) {

            	                nextJob = p; 
            	                availableMemory -= p.memoryRequired; // Allocates memory
            	                it.remove();
            	                break;
            	            }
            	        }
            	    }
            	}

            	if (nextJob != null) {

            	    nextJob.state = "ready"; // Changes process state to ready

            	    synchronized (readyQueueLock) { // Locks Ready Queue before editing it
            	        readyQueue.add(nextJob); // Adds process to Ready Queue
            	    }

            	    System.out.println("[Thread 2] Process P" + nextJob.processID +
            	            " moved to Ready Queue. Available memory: " +
            	            availableMemory + "MB");
            	}
                
                
                
            	// if Thread1 done and no more jobs can be admitted
            	if (thread1Done && jobQueue.isEmpty()) {
            	    break;
            	}

            	if (thread1Done && nextJob == null) {
            	    System.out.println("[Thread 2] No more processes can be admitted because of memory limit.");
            	    break;
            	}
                
                //this try pause Thread2 to reduce CPU storage 
                try { Thread.sleep(1); } catch (InterruptedException ignored) {}
            
            }// end while
            
            
            System.out.println("[Thread 2] All processes admitted and completed. Thread terminating.");
        
        
         }, "Thread-2-MemoryManager");

      
        // Start threads
       
        
        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();
        
        // ── Main Thread array for algorithm───────────────────────────────────────────
        List<PCB> allProcesses = new ArrayList<>();
       
        synchronized (readyQueueLock) {// Locks the ready queue before copying from it
        	
            allProcesses.addAll(readyQueue);
        }
        

        List<GanttEntry> gantt = new ArrayList<>(); //  to store Gantt chart entries
        List<PCB> completed   = new ArrayList<>(); //  to store completed processes


        switch (finalAlgorithm) {
            case "SJF":      runSJF(allProcesses, gantt, completed);      break;
            case "RR":       runRR(allProcesses, gantt, completed);       break;
            case "Priority": runPriority(allProcesses, gantt, completed); break;
        }

        // Signal Thread 2 to stop
        allDone = true;// Marks that all scheduling work is done

        // ── Output ────────────────────────────────────────────────────────────
        printGanttChart(gantt);
        printTable(completed);
        printMetrics(completed);

        if (finalAlgorithm.equals("Priority")) {
            printStarvationInfo(completed);// Prints starvation information for Priority Scheduling
        }

        System.out.println("\nReturning to main menu...\n");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  SCHEDULING ALGORITHMS
    // ═════════════════════════════════════════════════════════════════════════
    // ── SJF (Non-Preemptive) ─────────────────────────────────────────────────
    private static void runSJF(List<PCB> processes, List<GanttEntry> gantt, List<PCB> completed) {
        List<PCB> queue = new ArrayList<>(processes);
        int currentTime = 0;// Starts the CPU time

        while (!queue.isEmpty()) {
            // Sort by burst time, ties broken by arrival order
            queue.sort((a, b) -> a.burstTime != b.burstTime
                    ? a.burstTime - b.burstTime
                    : a.arrivalOrder - b.arrivalOrder);

            PCB p = queue.remove(0); //take first process
            p.state = "running";
            int burstStart = p.burstTime;

            if (p.startTime == -1) p.startTime = currentTime;// Sets the start time if it was not set before

            int endTime = currentTime + p.burstTime;
            gantt.add(new GanttEntry(p.processID, currentTime, endTime, burstStart, 0));

            p.terminationTime = endTime;
            p.turnaroundTime  = p.terminationTime - 0; // arrival = 0
            p.waitingTime     = p.turnaroundTime - p.burstTime;
            p.state           = "terminated";
            completed.add(p);
            
            synchronized (memoryLock) {// Locks memory before updating it
                availableMemory += p.memoryRequired;// Frees the memory used by the process
            } 
            
            currentTime = endTime;
        }
    }

    // ───────────────── Round Robin (q = 5ms) ──────────────────────────────────
   
    private static void runRR(List<PCB> processes, List<GanttEntry> gantt, List<PCB> completed) {
        int quantum = 5;
        Queue<PCB> queue = new LinkedList<>(processes); //FIFO queue for processes
        int currentTime = 0; // Increasing with every implementation

        while (!queue.isEmpty()) {
            PCB p = queue.poll(); // first process in queue 
            p.state = "running"; // change it's status 

            if (p.startTime == -1) p.startTime = currentTime;

            int burstStart = p.remainingBurst; // Stores the remaining burst before execution
            int execTime   = Math.min(quantum, p.remainingBurst);
            int endTime    = currentTime + execTime;

            gantt.add(new GanttEntry(p.processID, currentTime, endTime,
                    burstStart, burstStart - execTime)); 

            p.remainingBurst -= execTime;
            currentTime = endTime;

            if (p.remainingBurst == 0) { // Checks if it finished
                p.terminationTime = currentTime;
                p.turnaroundTime  = p.terminationTime;
                p.waitingTime     = p.turnaroundTime - p.burstTime;
                p.state           = "terminated";
                completed.add(p);
                
                synchronized (memoryLock) {// Locks memory before updating it
                    availableMemory += p.memoryRequired;
                } 
                
            } else {
                p.state = "ready";
                queue.add(p);
            }
        }
    } 


    // ── Priority (Non-Preemptive) with Aging ──────────────────────────────────
    private static void runPriority(List<PCB> processes, List<GanttEntry> gantt, List<PCB> completed) {
        List<PCB> queue = new ArrayList<>(processes);
        int currentTime = 0;

        

        // Track original priorities for starvation report
        int[] originalPriority = new int[processes.size() + 1];
    
        // Saves the original priority of each process.
        for (PCB p : queue) originalPriority[p.processID] = p.priority;

        // To store starved process IDs.
        List<Integer> starvedProcesses = new ArrayList<>();

        while (!queue.isEmpty()) {

        	int N = queue.size();
            int starvationhold = N * 5; // N × 5 ms
            
            // Sort by priority (lowest number = highest priority), ties by arrival order
            queue.sort((a, b) -> a.priority != b.priority ? a.priority - b.priority
                    : a.arrivalOrder - b.arrivalOrder);

            PCB p = queue.remove(0);
            int timeSlice = p.burstTime;  // Stores how long the other processes will wait

            // Update waiting time for all processes still in ready queue
            for (PCB waiting : queue) {

                int oldWaitingTime = waiting.timeInReadyQueue;
                waiting.timeInReadyQueue += timeSlice;

                // Check starvation: if waiting time is more than N × 5 ms
                if (waiting.timeInReadyQueue > starvationhold &&
                        !starvedProcesses.contains(waiting.processID)) {

                    starvedProcesses.add(waiting.processID);
                }

                // Aging is applied ONLY after the process has suffered starvation
                if (starvedProcesses.contains(waiting.processID)) {
 
                	//*************************************************************************
                    int oldAgingTicks = oldWaitingTime / 4; //old aging steps.
                    int newAgingTicks = waiting.timeInReadyQueue / 4;//new aging steps.
                    int agingTicks = newAgingTicks - oldAgingTicks; // Calculates how many priority improvements are needed


                    for (int t = 0; t < agingTicks; t++) {
                        if (waiting.priority > 1) {
                            waiting.priority--;
                        }
                    }
                }
            }

            p.state = "running";
            int burstStart = p.burstTime;

            if (p.startTime == -1) p.startTime = currentTime;

            int endTime = currentTime + p.burstTime;
            gantt.add(new GanttEntry(p.processID, currentTime, endTime, burstStart, 0));

            p.terminationTime = endTime;
            p.turnaroundTime  = p.terminationTime;
            p.waitingTime     = p.turnaroundTime - p.burstTime;

            if (starvedProcesses.contains(p.processID)) {
                p.state = "terminated(starved)";
            } else {
                p.state = "terminated";
            }

            completed.add(p);
 
            synchronized (memoryLock) {
                availableMemory += p.memoryRequired;
            }

            currentTime = endTime;
        }

        // Print starvation info
        for (PCB p : completed) {
            if (starvedProcesses.contains(p.processID)) {// Checks if the process was starved
                System.out.println("[Starvation] P" + p.processID +
                        " suffered starvation (original priority: " +
                        originalPriority[p.processID] + ")");
            }
        }
    }
            
            

            
            // ═════════════════════════════════════════════════════════════════════════
            //  OUTPUT METHODS
            // ═════════════════════════════════════════════════════════════════════════

            private static void printGanttChart(List<GanttEntry> gantt) {

            System.out.println("\n ________________________________________________________");
            System.out.println("|                        GANTT CHART                     |");
            System.out.println("|________________________________________________________|");
            System.out.println("\n");
            

            //  up
            for (GanttEntry e : gantt) {
                System.out.print("+-------");
            }
            System.out.println("+");

            //names
            for (GanttEntry e : gantt) {
                System.out.printf("|  P%-3d ", e.processID);
            }
            System.out.println("|");

            // down 
            for (GanttEntry e : gantt) {
                System.out.print("+-------");
            }
            System.out.println("+");

            // times
            System.out.printf("%-8d", 0);
            for (GanttEntry e : gantt) {
                System.out.printf("%-8d", e.endTime);
            }
            System.out.println();

            // Burst 
            for (GanttEntry e : gantt) {
                System.out.printf("%-8s", "[" + e.burstAtStart + "->" + e.burstAtEnd + "]");
            }
            System.out.println();

            System.out.println("\n------ Detailed Timeline ------");
            System.out.println("+------+--------+--------+");
            System.out.println("| PID  | Start  |  End   |");
            System.out.println("+------+--------+--------+");

            for (GanttEntry e : gantt) {
                System.out.printf("| P%-3d | %-6d | %-6d |%n",
                        e.processID, e.startTime, e.endTime);
            }

            System.out.println("+------+--------+--------+");
           
            }

            private static void printTable(List<PCB> completed) {

            System.out.println("\n ___________________________________________________________________________");
            System.out.println("|                           PROCESS STATISTICS TABLE                        |");
            System.out.println("|___________________________________________________________________________|");
            System.out.println("|  PID   | Burst(ms) | Start(ms)  | Terminate(ms)   | Wait(ms)    | TAT(ms) |");
            System.out.println("|--------|-----------|------------|-----------------|-------------|---------|");

            for (PCB p : completed) {
                 System.out.printf("| P%-5d | %-9d | %-10d | %-15d | %-11d | %-7d |%n",
                    p.processID, p.burstTime, p.startTime,
                    p.terminationTime, p.waitingTime, p.turnaroundTime);
            }
             System.out.println("|________|___________|____________|_________________|_____________|_________|");

             }

            private static void printMetrics(List<PCB> completed) {
            	//  Turnaround Time
            	int sumTAT = 0;
            	for (PCB p : completed) {
            	    sumTAT += p.turnaroundTime;
            	}
            	double avgTAT = (completed.size() > 0) ? (double) sumTAT / completed.size() : 0;

             	//  Waiting Time
            	int sumWait = 0;
            	for (PCB p : completed) {
            	    sumWait += p.waitingTime;
            	}
            	double avgWait = (completed.size() > 0) ? (double) sumWait / completed.size() : 0;

              System.out.println("\n __________________________________");
              System.out.println("|        PERFORMANCE METRICS       |");
              System.out.println("|__________________________________|");
              System.out.printf( "|  Avg Turnaround Time: %7.2f ms |%n", avgTAT);
              System.out.printf( "|  Avg Waiting Time   : %7.2f ms |%n", avgWait);
              System.out.println("|__________________________________|");

            }

            private static void printStarvationInfo(List<PCB> completed) {
            	System.out.println("\n  __________________________________");
            	System.out.println("|        STARVATION REPORT          |");
            	System.out.println("|___________________________________|");

                boolean y = false;
                for (PCB p : completed) {
                    if (p.state.contains("terminated(starved)")) {
                        System.out.println(" P" + p.processID + " suffered from starvation.");
                        y = true;
                    }
                }
                if (!y) System.out.println(" No processes suffered from starvation.");
            }
            
        }// end class
   