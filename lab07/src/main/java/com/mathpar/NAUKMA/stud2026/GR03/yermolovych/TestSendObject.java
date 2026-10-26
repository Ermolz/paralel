package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import mpi.MPI;
import mpi.MPIException;
import mpi.Status;

public class TestSendObject {
    private record Student(String surname, String group, int year) implements Serializable {
    }

    public static void sendObject(Object value, int destination, int tag)
            throws MPIException, IOException {
        byte[] data;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
            output.flush();
            data = bytes.toByteArray();
        }
        MPI.COMM_WORLD.send(data, data.length, MPI.BYTE, destination, tag);
    }

    public static Object recvObject(int source, int tag)
            throws MPIException, IOException, ClassNotFoundException {
        Status status = MPI.COMM_WORLD.probe(source, tag);
        byte[] data = new byte[status.getCount(MPI.BYTE)];
        MPI.COMM_WORLD.recv(data, data.length, MPI.BYTE, status.getSource(), status.getTag());
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return input.readObject();
        }
    }

    public static void main(String[] args)
            throws MPIException, IOException, ClassNotFoundException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int size = MPI.COMM_WORLD.getSize();
            int tag = 710;
            if (rank == 0) {
                Student student = new Student("yermolovych", "GR03", 2026);
                System.out.println("rank=0, send=" + student);
                for (int destination = 1; destination < size; destination++) {
                    sendObject(student, destination, tag);
                }
            } else {
                Object student = recvObject(0, tag);
                System.out.println("rank=" + rank + ", received=" + student);
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
mvn -q -o -Dlab.directory=lab07 compile
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp lab07/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestSendObject
Terminal output:
rank=0, send=Student[surname=yermolovych, group=GR03, year=2026]
rank=3, received=Student[surname=yermolovych, group=GR03, year=2026]
rank=1, received=Student[surname=yermolovych, group=GR03, year=2026]
rank=2, received=Student[surname=yermolovych, group=GR03, year=2026]
*/
