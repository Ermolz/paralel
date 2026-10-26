package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.io.IOException;
import com.mathpar.matrix.MatrixD;
import com.mathpar.number.Ring;
import com.mathpar.parallel.utils.MPITransport;
import mpi.MPI;
import mpi.MPIException;

public class MatrixMul8 {
    public static void main(String[] args)
            throws MPIException, IOException, ClassNotFoundException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            if (MPI.COMM_WORLD.getSize() != 8) {
                throw new IllegalArgumentException("Run with 8 processes.");
            }
            Ring ring = new Ring("Z[]");
            if (rank == 0) {
                int order = 8;
                int[][] aValues = new int[order][order];
                int[][] bValues = new int[order][order];
                for (int i = 0; i < order; i++) {
                    for (int j = 0; j < order; j++) {
                        aValues[i][j] = order * i + j + 1;
                        bValues[i][j] = i + j + 1;
                    }
                }
                MatrixD a = new MatrixD(aValues, ring);
                MatrixD b = new MatrixD(bValues, ring);
                System.out.println("A = " + a.toString(ring));
                System.out.println("B = " + b.toString(ring));

                MatrixD[] aa = a.splitTo4();
                MatrixD[] bb = b.splitTo4();
                int[] left = {0, 1, 0, 1, 2, 3, 2, 3};
                int[] right = {0, 2, 1, 3, 0, 2, 1, 3};
                for (int destination = 1; destination < 8; destination++) {
                    MPITransport.sendObjectArray(
                            new Object[]{aa[left[destination]], bb[right[destination]]},
                            0, 2, destination, 0);
                }

                MatrixD[] blocks = new MatrixD[4];
                blocks[0] = aa[0].multCU(bb[0], ring)
                        .add((MatrixD) MPITransport.recvObject(1, 3), ring);
                blocks[1] = (MatrixD) MPITransport.recvObject(2, 3);
                blocks[2] = (MatrixD) MPITransport.recvObject(4, 3);
                blocks[3] = (MatrixD) MPITransport.recvObject(6, 3);
                System.out.println("A * B = " + MatrixD.join(blocks).toString(ring));
            } else {
                Object[] blocks = new Object[2];
                MPITransport.recvObjectArray(blocks, 0, 2, 0, 0);
                MatrixD result = ((MatrixD) blocks[0]).multCU((MatrixD) blocks[1], ring);
                if (rank % 2 == 0) {
                    result = result.add((MatrixD) MPITransport.recvObject(rank + 1, 3), ring);
                    MPITransport.sendObject(result, 0, 3);
                    System.out.println("rank=" + rank + ", block=" + (rank / 2) + ", sent_to=0");
                } else {
                    MPITransport.sendObject(result, rank - 1, 3);
                    System.out.println("rank=" + rank + ", partial_product_sent_to=" + (rank - 1));
                }
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
mpirun --oversubscribe --bind-to none --timeout 60 -np 8 java -cp "lab08/target/classes:lib/mpi.jar:lib/mathpar/*" com.mathpar.NAUKMA.stud2026.GR03.yermolovych.MatrixMul8
Terminal output:
A = [[1, 2, 3, 4, 5, 6, 7, 8],
[9, 10, 11, 12, 13, 14, 15, 16],
[17, 18, 19, 20, 21, 22, 23, 24],
[25, 26, 27, 28, 29, 30, 31, 32],
[33, 34, 35, 36, 37, 38, 39, 40],
[41, 42, 43, 44, 45, 46, 47, 48],
[49, 50, 51, 52, 53, 54, 55, 56],
[57, 58, 59, 60, 61, 62, 63, 64]]
B = [[1, 2, 3, 4, 5, 6, 7, 8],
[2, 3, 4, 5, 6, 7, 8, 9],
[3, 4, 5, 6, 7, 8, 9, 10],
[4, 5, 6, 7, 8, 9, 10, 11],
[5, 6, 7, 8, 9, 10, 11, 12],
[6, 7, 8, 9, 10, 11, 12, 13],
[7, 8, 9, 10, 11, 12, 13, 14],
[8, 9, 10, 11, 12, 13, 14, 15]]
rank=3, partial_product_sent_to=2
rank=1, partial_product_sent_to=0
rank=5, partial_product_sent_to=4
rank=7, partial_product_sent_to=6
rank=2, block=1, sent_to=0
A * B = [[204, 240, 276, 312, 348, 384, 420, 456],
[492, 592, 692, 792, 892, 992, 1092, 1192],
[780, 944, 1108, 1272, 1436, 1600, 1764, 1928],
[1068, 1296, 1524, 1752, 1980, 2208, 2436, 2664],
[1356, 1648, 1940, 2232, 2524, 2816, 3108, 3400],
[1644, 2000, 2356, 2712, 3068, 3424, 3780, 4136],
[1932, 2352, 2772, 3192, 3612, 4032, 4452, 4872],
[2220, 2704, 3188, 3672, 4156, 4640, 5124, 5608]]
rank=4, block=2, sent_to=0
rank=6, block=3, sent_to=0
*/
