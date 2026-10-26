package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.io.IOException;
import com.mathpar.matrix.MatrixD;
import com.mathpar.number.Ring;
import com.mathpar.parallel.utils.MPITransport;
import mpi.MPI;
import mpi.MPIException;

public class MatrixMul4 {
    public static MatrixD mmultiply(MatrixD a, MatrixD b, MatrixD c, MatrixD d, Ring ring) {
        return a.multCU(b, ring).add(c.multCU(d, ring), ring);
    }

    public static void main(String[] args)
            throws MPIException, IOException, ClassNotFoundException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            if (MPI.COMM_WORLD.getSize() != 4) {
                throw new IllegalArgumentException("Run with 4 processes.");
            }
            Ring ring = new Ring("Z[]");
            if (rank == 0) {
                MatrixD a = new MatrixD(new int[][]{
                    {1, 2, 3, 4}, {5, 6, 7, 8}, {9, 10, 11, 12}, {13, 14, 15, 16}
                }, ring);
                MatrixD b = new MatrixD(new int[][]{
                    {2, 0, 1, 3}, {1, 2, 0, 1}, {3, 1, 2, 0}, {0, 2, 1, 2}
                }, ring);
                System.out.println("A = " + a.toString(ring));
                System.out.println("B = " + b.toString(ring));

                MatrixD[] aa = a.splitTo4();
                MatrixD[] bb = b.splitTo4();
                MPITransport.sendObjectArray(new Object[]{aa[0], bb[1], aa[1], bb[3]},
                        0, 4, 1, 1);
                MPITransport.sendObjectArray(new Object[]{aa[2], bb[0], aa[3], bb[2]},
                        0, 4, 2, 2);
                MPITransport.sendObjectArray(new Object[]{aa[2], bb[1], aa[3], bb[3]},
                        0, 4, 3, 3);

                MatrixD[] blocks = new MatrixD[4];
                blocks[0] = mmultiply(aa[0], bb[0], aa[1], bb[2], ring);
                for (int source = 1; source < 4; source++) {
                    blocks[source] = (MatrixD) MPITransport.recvObject(source, source);
                }
                System.out.println("A * B = " + MatrixD.join(blocks).toString(ring));
            } else {
                Object[] blocks = new Object[4];
                MPITransport.recvObjectArray(blocks, 0, 4, 0, rank);
                MatrixD result = mmultiply((MatrixD) blocks[0], (MatrixD) blocks[1],
                        (MatrixD) blocks[2], (MatrixD) blocks[3], ring);
                MPITransport.sendObject(result, 0, rank);
                System.out.println("rank=" + rank + ", block=" + rank + ", sent_to=0");
            }
        } finally {
            MPI.Finalize();
        }
    }
}

/*
Run:
cd ~/paralel
source scripts/env.sh
mvn -q -o -Dlab.directory=lab08 compile
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp "lab08/target/classes:lib/mpi.jar:lib/mathpar/*" com.mathpar.NAUKMA.stud2026.GR03.yermolovych.MatrixMul4
Terminal output:
A = [[1, 2, 3, 4],
[5, 6, 7, 8],
[9, 10, 11, 12],
[13, 14, 15, 16]]
B = [[2, 0, 1, 3],
[1, 2, 0, 1],
[3, 1, 2, 0],
[0, 2, 1, 2]]
A * B = [[13, 15, 11, 13],
[37, 35, 27, 37],
[61, 55, 43, 61],
[85, 75, 59, 85]]
rank=1, block=1, sent_to=0
rank=2, block=2, sent_to=0
rank=3, block=3, sent_to=0
*/
