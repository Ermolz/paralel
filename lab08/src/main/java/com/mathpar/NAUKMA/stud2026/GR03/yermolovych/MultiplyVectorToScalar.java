package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.io.IOException;
import java.util.Arrays;
import com.mathpar.number.Element;
import com.mathpar.number.NumberZ;
import com.mathpar.number.Ring;
import com.mathpar.number.VectorS;
import com.mathpar.parallel.utils.MPITransport;
import mpi.MPI;
import mpi.MPIException;

public class MultiplyVectorToScalar {
    public static void main(String[] args)
            throws MPIException, IOException, ClassNotFoundException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int size = MPI.COMM_WORLD.getSize();
            int order = 8;
            if (size > order) {
                throw new IllegalArgumentException("Use from 1 to 8 processes.");
            }
            Ring ring = new Ring("Z[]");
            Element scalar = NumberZ.valueOf(5);
            int count = order / size;
            int rootCount = order - count * (size - 1);
            if (rank == 0) {
                VectorS b = new VectorS(new int[]{-3, -2, -1, 0, 1, 2, 3, 4});
                System.out.println("B = " + b.toString(ring));
                System.out.println("S = " + scalar.toString(ring));
                for (int destination = 1; destination < size; destination++) {
                    int start = rootCount + (destination - 1) * count;
                    Element[] block = Arrays.copyOfRange(b.V, start, start + count);
                    MPITransport.sendObject(block, destination, 100 + destination);
                }

                Element[] result = new Element[order];
                for (int i = 0; i < rootCount; i++) {
                    result[i] = b.V[i].multiply(scalar, ring);
                }
                System.out.println("rank=0, offset=0, result="
                        + new VectorS(Arrays.copyOf(result, rootCount)).toString(ring));
                for (int source = 1; source < size; source++) {
                    Element[] block = (Element[]) MPITransport.recvObject(source, 100 + source);
                    System.arraycopy(block, 0, result, rootCount + (source - 1) * count, count);
                }
                System.out.println("B * S = " + new VectorS(result).toString(ring));
            } else {
                Element[] block = (Element[]) MPITransport.recvObject(0, 100 + rank);
                Element[] result = new Element[block.length];
                for (int i = 0; i < block.length; i++) {
                    result[i] = block[i].multiply(scalar, ring);
                }
                MPITransport.sendObject(result, 0, 100 + rank);
                System.out.println("rank=" + rank + ", offset=" + (rootCount + (rank - 1) * count)
                        + ", result=" + new VectorS(result).toString(ring));
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
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp "lab08/target/classes:lib/mpi.jar:lib/mathpar/*" com.mathpar.NAUKMA.stud2026.GR03.yermolovych.MultiplyVectorToScalar
Terminal output:
B = [-3, -2, -1, 0, 1, 2, 3, 4]
S = 5
rank=0, offset=0, result=[-15, -10]
B * S = [-15, -10, -5, 0, 5, 10, 15, 20]
rank=1, offset=2, result=[-5, 0]
rank=3, offset=6, result=[15, 20]
rank=2, offset=4, result=[5, 10]
*/
