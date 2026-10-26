package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import mpi.MPI;
import mpi.MPIException;
import mpi.Request;
import mpi.Status;

public class TestSendObjects {
    public static Request sendObjects(Object[] values, int destination, int tag)
            throws MPIException, IOException {
        byte[] data;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            for (Object value : values) {
                output.writeObject(value);
            }
            output.flush();
            data = bytes.toByteArray();
        }
        ByteBuffer buffer = MPI.newByteBuffer(data.length);
        buffer.put(data);
        buffer.flip();
        return MPI.COMM_WORLD.iSend(buffer, data.length, MPI.BYTE, destination, tag);
    }

    public static Object[] recvObjects(int count, int source, int tag)
            throws MPIException, IOException, ClassNotFoundException {
        Status status = MPI.COMM_WORLD.probe(source, tag);
        byte[] data = new byte[status.getCount(MPI.BYTE)];
        MPI.COMM_WORLD.recv(data, data.length, MPI.BYTE, status.getSource(), status.getTag());
        Object[] values = new Object[count];
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(data))) {
            for (int i = 0; i < count; i++) {
                values[i] = input.readObject();
            }
        }
        return values;
    }

    public static void main(String[] args)
            throws MPIException, IOException, ClassNotFoundException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int size = MPI.COMM_WORLD.getSize();
            int tag = 720;
            if (rank == 0) {
                Object[] values = {"yermolovych", "GR03", 2026, new int[]{10, 20, 30}};
                System.out.println("rank=0, send=" + Arrays.deepToString(values));
                Request[] requests = new Request[size - 1];
                for (int destination = 1; destination < size; destination++) {
                    requests[destination - 1] = sendObjects(values, destination, tag);
                }
                for (Request request : requests) {
                    request.waitFor();
                }
            } else {
                Object[] values = recvObjects(4, 0, tag);
                System.out.println("rank=" + rank + ", received=" + Arrays.deepToString(values));
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
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp lab07/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestSendObjects
Terminal output:
rank=0, send=[yermolovych, GR03, 2026, [10, 20, 30]]
rank=3, received=[yermolovych, GR03, 2026, [10, 20, 30]]
rank=1, received=[yermolovych, GR03, 2026, [10, 20, 30]]
rank=2, received=[yermolovych, GR03, 2026, [10, 20, 30]]
*/
