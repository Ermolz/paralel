package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Locale;
import mpi.MPI;
import mpi.MPIException;
import mpi.Request;

public class TestBlockingAndNonblocking {
    public static void main(String[] args) throws MPIException, InterruptedException {
        args = MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            if (MPI.COMM_WORLD.getSize() != 2) {
                throw new IllegalArgumentException("Run with exactly 2 MPI processes.");
            }
            String mode = args.length > 0 ? args[0] : "nonblocking";
            int n = args.length > 1 ? Integer.parseInt(args[1]) : 5;
            if (n < 1 || !Arrays.asList("blocking", "delayed", "unsafe", "nonblocking").contains(mode)) {
                throw new IllegalArgumentException("Use blocking|delayed|unsafe|nonblocking and a positive array length.");
            }
            int peer = 1 - rank;
            int[] outgoing = new int[n];
            int[] incoming = new int[n];
            for (int i = 0; i < n; i++) {
                outgoing[i] = rank * 1000 + i;
            }
            IntBuffer sendBuffer = null;
            IntBuffer receiveBuffer = null;
            if (mode.equals("nonblocking")) {
                sendBuffer = MPI.newIntBuffer(n);
                receiveBuffer = MPI.newIntBuffer(n);
                for (int i = 0; i < n; i++) {
                    sendBuffer.put(i, outgoing[i]);
                }
            }

            MPI.COMM_WORLD.barrier();
            long started = System.nanoTime();
            switch (mode) {
                case "blocking":
                case "delayed":
                    if (rank == 0) {
                        MPI.COMM_WORLD.send(outgoing, n, MPI.INT, 1, 0);
                    } else {
                        if (mode.equals("delayed")) {
                            Thread.sleep(300);
                        }
                        MPI.COMM_WORLD.recv(incoming, n, MPI.INT, 0, 0);
                    }
                    break;
                case "unsafe":
                    System.out.println("mode=unsafe, rank=" + rank + ", stage=before_send, count=" + n);
                    System.out.flush();
                    MPI.COMM_WORLD.send(outgoing, n, MPI.INT, peer, rank);
                    MPI.COMM_WORLD.recv(incoming, n, MPI.INT, peer, peer);
                    break;
                case "nonblocking":
                    Request receive = MPI.COMM_WORLD.iRecv(receiveBuffer, n, MPI.INT, peer, peer);
                    Request send = MPI.COMM_WORLD.iSend(sendBuffer, n, MPI.INT, peer, rank);
                    receive.waitFor();
                    send.waitFor();
                    for (int i = 0; i < n; i++) {
                        incoming[i] = receiveBuffer.get(i);
                    }
                    break;
            }
            double elapsed = (System.nanoTime() - started) / 1_000_000.0;
            boolean oneWay = mode.equals("blocking") || mode.equals("delayed");
            boolean received = !oneWay || rank == 1;
            int[] data = received ? incoming : outgoing;
            if (received) {
                for (int i = 0; i < n; i++) {
                    if (data[i] != peer * 1000 + i) {
                        throw new IllegalStateException("Received data mismatch at index " + i);
                    }
                }
            }
            long checksum = 0;
            for (int value : data) {
                checksum += value;
            }
            System.out.println(String.format(Locale.ROOT,
                    "mode=%s, rank=%d, role=%s, count=%d, first=%d, last=%d, checksum=%d, elapsed_ms=%.3f",
                    mode, rank, received ? "received" : "sent", n,
                    data[0], data[n - 1], checksum, elapsed));
        } finally {
            MPI.Finalize();
        }
    }
}

/*
Run:
cd ~/paralel
source scripts/env.sh
mvn -q -o -Dlab.directory=lab04 compile

mpirun --oversubscribe --bind-to none --timeout 60 -np 2 java -cp lab04/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestBlockingAndNonblocking blocking 5
Terminal output:
mode=blocking, rank=0, role=sent, count=5, first=0, last=4, checksum=10, elapsed_ms=0.031
mode=blocking, rank=1, role=received, count=5, first=0, last=4, checksum=10, elapsed_ms=0.049

Run:
mpirun --oversubscribe --bind-to none --timeout 60 -np 2 java -cp lab04/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestBlockingAndNonblocking delayed 5
Terminal output:
mode=delayed, rank=0, role=sent, count=5, first=0, last=4, checksum=10, elapsed_ms=0.038
mode=delayed, rank=1, role=received, count=5, first=0, last=4, checksum=10, elapsed_ms=303.062

Run:
mpirun --oversubscribe --bind-to none --timeout 60 -np 2 java -cp lab04/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestBlockingAndNonblocking delayed 262144
Terminal output:
mode=delayed, rank=0, role=sent, count=262144, first=0, last=262143, checksum=34359607296, elapsed_ms=301.310
mode=delayed, rank=1, role=received, count=262144, first=0, last=262143, checksum=34359607296, elapsed_ms=301.505

Run:
mpirun --oversubscribe --bind-to none --timeout 5 -np 2 java -cp lab04/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestBlockingAndNonblocking unsafe 5
Terminal output:
mode=unsafe, rank=0, stage=before_send, count=5
mode=unsafe, rank=1, stage=before_send, count=5
mode=unsafe, rank=1, role=received, count=5, first=0, last=4, checksum=10, elapsed_ms=18.697
mode=unsafe, rank=0, role=received, count=5, first=1000, last=1004, checksum=5010, elapsed_ms=18.673


Run:
mpirun --oversubscribe --bind-to none --timeout 5 -np 2 java -cp lab04/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestBlockingAndNonblocking unsafe 262144
Terminal output:
mode=unsafe, rank=1, stage=before_send, count=262144
mode=unsafe, rank=0, stage=before_send, count=262144
--------------------------------------------------------------------------
The user-provided time limit for job execution has been reached:

  Timeout: 5 seconds

The job will now be aborted.  Please check your code and/or
adjust/remove the job execution time limit (as specified by --timeout
command line option or MPIEXEC_TIMEOUT environment variable).
--------------------------------------------------------------------------

Run:
mpirun --oversubscribe --bind-to none --timeout 60 -np 2 java -cp lab04/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestBlockingAndNonblocking nonblocking 262144
Terminal output:
mode=nonblocking, rank=1, role=received, count=262144, first=0, last=262143, checksum=34359607296, elapsed_ms=3.932
mode=nonblocking, rank=0, role=received, count=262144, first=1000, last=263143, checksum=34621751296, elapsed_ms=3.996
*/
