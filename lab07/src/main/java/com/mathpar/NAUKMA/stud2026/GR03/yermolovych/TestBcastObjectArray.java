package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import mpi.MPI;
import mpi.MPIException;

public class TestBcastObjectArray {
    public static void bcastObjectArray(Object[] values, int count, int root)
            throws MPIException, IOException, ClassNotFoundException {
        int rank = MPI.COMM_WORLD.getRank();
        int[] length = new int[1];
        byte[] data = null;
        if (rank == root) {
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                for (int i = 0; i < count; i++) {
                    output.writeObject(values[i]);
                }
                output.flush();
                data = bytes.toByteArray();
            }
            length[0] = data.length;
        }

        MPI.COMM_WORLD.bcast(length, 1, MPI.INT, root);
        if (rank != root) {
            data = new byte[length[0]];
        }
        MPI.COMM_WORLD.bcast(data, data.length, MPI.BYTE, root);

        if (rank != root) {
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(data))) {
                for (int i = 0; i < count; i++) {
                    values[i] = input.readObject();
                }
            }
        }
    }

    public static void main(String[] args)
            throws MPIException, IOException, ClassNotFoundException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int root = 0;
            int count = 4;
            Object[] values = rank == root
                    ? new Object[]{"yermolovych", "GR03", 2026, new int[]{10, 20, 30}}
                    : new Object[count];
            bcastObjectArray(values, count, root);
            System.out.println("rank=" + rank + ", objects=" + Arrays.deepToString(values));
        } finally {
            MPI.Finalize();
        }
    }
}

/*
Run:
cd ~/paralel
source scripts/env.sh
mvn -q -o -Dlab.directory=lab07 compile
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp lab07/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestBcastObjectArray
Terminal output:
rank=0, objects=[yermolovych, GR03, 2026, [10, 20, 30]]
rank=1, objects=[yermolovych, GR03, 2026, [10, 20, 30]]
rank=3, objects=[yermolovych, GR03, 2026, [10, 20, 30]]
rank=2, objects=[yermolovych, GR03, 2026, [10, 20, 30]]
*/
