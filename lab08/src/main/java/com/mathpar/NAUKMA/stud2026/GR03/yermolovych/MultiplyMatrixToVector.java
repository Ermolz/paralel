package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.io.IOException;
import java.util.Arrays;
import com.mathpar.matrix.MatrixS;
import com.mathpar.number.Element;
import com.mathpar.number.Ring;
import com.mathpar.number.VectorS;
import com.mathpar.parallel.utils.MPITransport;
import mpi.MPI;
import mpi.MPIException;

public class MultiplyMatrixToVector {
    private static MatrixS rows(MatrixS matrix, int start, int count) {
        Element[][] values = Arrays.copyOfRange(matrix.M, start, start + count);
        int[][] columns = Arrays.copyOfRange(matrix.col, start, start + count);
        return new MatrixS(count, matrix.colNumb, values, columns);
    }

    public static void main(String[] args)
            throws MPIException, IOException, ClassNotFoundException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int size = MPI.COMM_WORLD.getSize();
            int order = 16;
            if (size > order) {
                throw new IllegalArgumentException("Use from 1 to 16 processes.");
            }
            Ring ring = new Ring("Z[]");
            int count = order / size;
            int rootCount = order - count * (size - 1);
            if (rank == 0) {
                int[][] values = new int[order][order];
                int[] vectorValues = new int[order];
                for (int i = 0; i < order; i++) {
                    vectorValues[i] = i + 1;
                    for (int j = 0; j < order; j++) {
                        values[i][j] = i + j + 1;
                    }
                }
                MatrixS a = new MatrixS(values, ring);
                VectorS b = new VectorS(vectorValues);
                System.out.println("A = " + a.toString(ring));
                System.out.println("B = " + b.toString(ring));

                for (int destination = 1; destination < size; destination++) {
                    MatrixS block = rows(a, (destination - 1) * count, count);
                    MPITransport.sendObject(block, destination, 1);
                    MPITransport.sendObject(b, destination, 2);
                }

                int rootStart = (size - 1) * count;
                VectorS local = rows(a, rootStart, rootCount).multiplyByColumn(b.transpose(ring), ring);
                Element[] result = new Element[order];
                System.arraycopy(local.V, 0, result, rootStart, rootCount);
                System.out.println("rank=0, row_start=" + rootStart + ", result=" + local.toString(ring));
                for (int source = 1; source < size; source++) {
                    VectorS block = (VectorS) MPITransport.recvObject(source, source);
                    System.arraycopy(block.V, 0, result, (source - 1) * count, count);
                }
                System.out.println("A * B = " + new VectorS(result).toString(ring));
            } else {
                MatrixS a = (MatrixS) MPITransport.recvObject(0, 1);
                VectorS b = (VectorS) MPITransport.recvObject(0, 2);
                VectorS result = a.multiplyByColumn(b.transpose(ring), ring);
                MPITransport.sendObject(result, 0, rank);
                System.out.println("rank=" + rank + ", row_start=" + ((rank - 1) * count)
                        + ", result=" + result.toString(ring));
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
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp "lab08/target/classes:lib/mpi.jar:lib/mathpar/*" com.mathpar.NAUKMA.stud2026.GR03.yermolovych.MultiplyMatrixToVector
Terminal output:
A = 
[[1,  2,  3,  4,  5,  6,  7,  8,  9,  10, 11, 12, 13, 14, 15, 16]
 [2,  3,  4,  5,  6,  7,  8,  9,  10, 11, 12, 13, 14, 15, 16, 17]
 [3,  4,  5,  6,  7,  8,  9,  10, 11, 12, 13, 14, 15, 16, 17, 18]
 [4,  5,  6,  7,  8,  9,  10, 11, 12, 13, 14, 15, 16, 17, 18, 19]
 [5,  6,  7,  8,  9,  10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20]
 [6,  7,  8,  9,  10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21]
 [7,  8,  9,  10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22]
 [8,  9,  10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23]
 [9,  10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24]
 [10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25]
 [11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26]
 [12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27]
 [13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28]
 [14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29]
 [15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30]
 [16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31]]
B = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]
rank=1, row_start=0, result=[1496, 1632, 1768, 1904]
rank=2, row_start=4, result=[2040, 2176, 2312, 2448]
rank=0, row_start=12, result=[3128, 3264, 3400, 3536]
A * B = [1496, 1632, 1768, 1904, 2040, 2176, 2312, 2448, 2584, 2720, 2856, 2992, 3128, 3264, 3400, 3536]
rank=3, row_start=8, result=[2584, 2720, 2856, 2992]
*/
