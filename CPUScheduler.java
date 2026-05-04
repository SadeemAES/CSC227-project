package project;



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

        ///////////////////////////////////// haya part 1 start here 
        ///////////////////////////////////////////


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
        System.out.print("Choice [1/2/3]: ");
        int choice = Integer.parseInt(sc.nextLine().trim());

        String algorithm;
        switch (choice) {
            case 1:  algorithm = "SJF";      break;
            case 2:  algorithm = "RR";       break;
            case 3:  algorithm = "Priority"; break;
            default: System.out.println("Invalid choice. Defaulting to SJF."); algorithm = "SJF";
        }

        System.out.println("\nRunning " + algorithm + " scheduler...\n");

        final String finalFilePath = "job.txt" ;//----------------------------------------------------------------------
        final String finalAlgorithm = algorithm;

        // ── Thread 1: Read file then add it to the Job Queue ──────────────────────────────────
        Thread thread1 = new Thread(() -> {
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

                    PCB pcb = new PCB(id, burst, priority, memory, order++);
                    synchronized (jobQueueLock) {
                        jobQueue.add(pcb);
                        System.out.println("[Thread 1] Process P" + id +
                                " added to Job Queue (Burst=" + burst +
                                "ms, Priority=" + priority +
                                ", Memory=" + memory + "MB)");
                    }
                }
                br.close();
            } catch (IOException e) {
                System.err.println("[Thread 1] Error reading file: " + e.getMessage());
            }
            thread1Done = true;
            System.out.println("[Thread 1] All processes loaded. Thread terminating.");
        }, "Thread-1-FileLoader");

        // ── Thread 2: from Job Queue to Ready Queue (memory check) ─────────────────
        Thread thread2 = new Thread(() -> {
            while (true) {
                PCB nextJob = null;

                synchronized (jobQueueLock) {
                    if (!jobQueue.isEmpty()) {
                        nextJob = jobQueue.peek();
                    }
                }

                if (nextJob != null) {
                    synchronized (memoryLock) {
                        if (availableMemory >= nextJob.memoryRequired) {
                            synchronized (jobQueueLock) {
                                jobQueue.poll(); // remove from job queue
                            }
                            availableMemory -= nextJob.memoryRequired;
                            nextJob.state = "ready";
                            synchronized (readyQueueLock) {
                                readyQueue.add(nextJob);
                            }
                            System.out.println("[Thread 2] Process P" + nextJob.processID +
                                    " moved to Ready Queue. Available memory: " +
                                    availableMemory + "MB");
                        }
                    }
                }
                
                
                ///////////////////////////////////// Nora part 2 start here 
                ////////////////////////////////////////

                // if Thread1 done AND job queue empty AND all processes finished
                if (thread1Done && jobQueue.isEmpty() && allDone) {
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

        // Wait for Thread 1 to finish loading
        thread1.join();

        //wait for Thread 2 to transfer to the ready queue
        Thread.sleep(50);

        // ── Main Thread array for algorithm───────────────────────────────────────────
        List<PCB> allProcesses = new ArrayList<>();
       
        synchronized (readyQueueLock) {
            allProcesses.addAll(readyQueue);
        }
        
        
        // Also grab remaining in job queue (in case the process did not enter the ready queue)
        synchronized (jobQueueLock) {
            allProcesses.addAll(jobQueue);
        }

        List<GanttEntry> gantt = new ArrayList<>();
        List<PCB> completed   = new ArrayList<>();

        switch (finalAlgorithm) {
            case "SJF":      runSJF(allProcesses, gantt, completed);      break;
            case "RR":       runRR(allProcesses, gantt, completed);       break;
            case "Priority": runPriority(allProcesses, gantt, completed); break;
        }

        // Signal Thread 2 to stop
        allDone = true;
        thread2.join();

        // ── Output ────────────────────────────────────────────────────────────
        printGanttChart(gantt);
        printTable(completed);
        printMetrics(completed);

        if (finalAlgorithm.equals("Priority")) {
            printStarvationInfo(completed);
        }

        sc.close();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  SCHEDULING ALGORITHMS
    // ═════════════════════════════════════════════════════════════════════════
    // ── SJF (Non-Preemptive) ─────────────────────────────────────────────────
    private static void runSJF(List<PCB> processes, List<GanttEntry> gantt, List<PCB> completed) {
        List<PCB> queue = new ArrayList<>(processes);
        int currentTime = 0;

        while (!queue.isEmpty()) {
            // Sort by burst time, ties broken by arrival order
            queue.sort((a, b) -> a.burstTime != b.burstTime
                    ? a.burstTime - b.burstTime
                    : a.arrivalOrder - b.arrivalOrder);

            PCB p = queue.remove(0); //take first process
            p.state = "running";
            int burstStart = p.burstTime;

            if (p.startTime == -1) p.startTime = currentTime;

            int endTime = currentTime + p.burstTime;
            gantt.add(new GanttEntry(p.processID, currentTime, endTime, burstStart, 0));

            p.terminationTime = endTime;
            p.turnaroundTime  = p.terminationTime - 0; // arrival = 0
            p.waitingTime     = p.turnaroundTime - p.burstTime;
            p.state           = "terminated";
            completed.add(p);
            
            synchronized (memoryLock) {
                availableMemory += p.memoryRequired;
            } 
            
            currentTime = endTime;
        }
    }

    // ~~~~~~~~~~~ Round Robin (q = 5ms) ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
   
    private static void runRR(List<PCB> processes, List<GanttEntry> gantt, List<PCB> completed) {
        int quantum = 5;
        Queue<PCB> queue = new LinkedList<>(processes); //FIFO queue for processes
        int currentTime = 0; // Increasing with every implementation

        while (!queue.isEmpty()) {
            PCB p = queue.poll(); // first process in queue 
            p.state = "running"; // change it's status 

            if (p.startTime == -1) p.startTime = currentTime;

            int burstStart = p.remainingBurst; 
            int execTime   = Math.min(quantum, p.remainingBurst);
            int endTime    = currentTime + execTime;

            gantt.add(new GanttEntry(p.processID, currentTime, endTime,
                    burstStart, burstStart - execTime)); 

            p.remainingBurst -= execTime;
            currentTime = endTime;

            if (p.remainingBurst == 0) {
                p.terminationTime = currentTime;
                p.turnaroundTime  = p.terminationTime;
                p.waitingTime     = p.turnaroundTime - p.burstTime;
                p.state           = "terminated";
                completed.add(p);
                
                synchronized (memoryLock) {
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
        int N = queue.size();
        int starvationhold = N * 5; // N × 5 ms

        // Track original priorities for starvation report
        int[] originalPriority = new int[processes.size() + 1];
        for (PCB p : queue) originalPriority[p.processID] = p.priority;

        List<Integer> starvedProcesses = new ArrayList<>();

        while (!queue.isEmpty()) {
            // Apply aging: every 4ms, decrease priority number by 1 for waiting processes
            for (PCB p : queue) {
                p.timeInReadyQueue += 1; // simulate 1ms tick
                if (p.timeInReadyQueue % 4 == 0 && p.priority > 1) {
                    p.priority--; // improve priority
                }
                // Check starvation
                if (p.timeInReadyQueue > starvationhold) {
                    if (!starvedProcesses.contains(p.processID)) {
                        starvedProcesses.add(p.processID);
                    }
                }
            }

            // Sort by priority (lowest number = highest priority), ties by arrival order
            queue.sort((a, b) -> a.priority != b.priority ? a.priority - b.priority
                    : a.arrivalOrder - b.arrivalOrder);

            PCB p = queue.remove(0);
            p.state = "running";
            int burstStart = p.burstTime;

            if (p.startTime == -1) p.startTime = currentTime;

            int endTime = currentTime + p.burstTime;
            gantt.add(new GanttEntry(p.processID, currentTime, endTime, burstStart, 0));

            p.terminationTime = endTime;
            p.turnaroundTime  = p.terminationTime;
            p.waitingTime     = p.turnaroundTime - p.burstTime;
            p.state           = "terminated";

            // Attach starvation flag
            if (starvedProcesses.contains(p.processID)) {
                p.state = "terminated(starved)";
            }

            completed.add(p);
            
            synchronized (memoryLock) {
                availableMemory += p.memoryRequired;
            }  
            
            currentTime = endTime;
        }

        // Store starvation info for printing
        for (PCB p : completed) {
            if (starvedProcesses.contains(p.processID)) {
                System.out.println("[Starvation] P" + p.processID +
                        " suffered starvation (original priority: " +
                        originalPriority[p.processID] + ")");
            }
        }
    }
     
            
            
///////////////////////////////////// Shouq part 4 start here 
////////////////////////////////////////

            
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
                        System.out.println(" P" + p.processID + " There is starvation.");
                        y = true;
                    }
                }
                if (!y) System.out.println(" No processes suffered from starvation.");
            }
            
        }// end class
 