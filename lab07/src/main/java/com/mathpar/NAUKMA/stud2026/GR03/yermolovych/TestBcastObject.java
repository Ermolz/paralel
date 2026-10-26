package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import mpi.MPI;
import mpi.MPIException;

public class TestBcastObject {
    private record Student(String surname, String group, int year) implements Serializable {
    }

    public static Object bcastObject(Object value, int root)
            throws MPIException, IOException, ClassNotFoundException {
        int rank = MPI.COMM_WORLD.getRank();
        int[] length = new int[1];
        byte[] data = null;
        if (rank == root) {
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(value);
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
                return input.readObject();
            }
        }
        return value;
    }

    public static void main(String[] args)
            throws MPIException, IOException, ClassNotFoundException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int root = 0;
            Object student = rank == root ? new Student("yermolovych", "GR03", 2026) : null;
            student = bcastObject(student, root);
            System.out.println("rank=" + rank + ", object=" + student);
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
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp lab07/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestBcastObject
Terminal output:
rank=0, object=Student[surname=yermolovych, group=GR03, year=2026]
rank=3, object=Student[surname=yermolovych, group=GR03, year=2026]
rank=2, object=Student[surname=yermolovych, group=GR03, year=2026]
rank=1, object=Student[surname=yermolovych, group=GR03, year=2026]
*/
