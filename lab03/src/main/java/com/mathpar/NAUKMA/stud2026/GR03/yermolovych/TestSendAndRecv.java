package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.util.Arrays;
import java.util.Random;
import mpi.MPI;
import mpi.MPIException;

public class TestSendAndRecv {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int size = MPI.COMM_WORLD.getSize();
            int n = 5;
            double[] data = new double[n];

            if (rank == 0) {
                Random random = new Random(2026);
                for (int i = 0; i < n; i++) {
                    data[i] = random.nextDouble();
                }
                for (int destination = 1; destination < size; destination++) {
                    MPI.COMM_WORLD.send(data, n, MPI.DOUBLE, destination, 3000);
                }
            } else {
                MPI.COMM_WORLD.recv(data, n, MPI.DOUBLE, 0, 3000);
            }

            System.out.println(String.format(
                    "mode=send/recv, rank=%d, size=%d, role=%s, data=%s",
                    rank, size, rank == 0 ? "source" : "receiver", Arrays.toString(data)));
        } finally {
            MPI.Finalize();
        }
    }
}

/*
Lab 3, sections 1.3.1-1.3.5: send/recv.
Run:
cd ~/paralel
source scripts/env.sh
mvn -q -o -Dlab.directory=lab03 compile

mpirun --oversubscribe --bind-to none --timeout 60 -np 2 java -cp lab03/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestSendAndRecv
Terminal output:
mode=send/recv, rank=1, size=2, role=receiver, data=[0.6186327814734252, 0.849209341093155, 0.7903839677394705, 0.5296053232288097, 0.8416668624371814]
mode=send/recv, rank=0, size=2, role=source, data=[0.6186327814734252, 0.849209341093155, 0.7903839677394705, 0.5296053232288097, 0.8416668624371814]

mpirun --oversubscribe --bind-to none --timeout 60 -np 8 java -cp lab03/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestSendAndRecv
Terminal output:
mode=send/recv, rank=0, size=8, role=source, data=[0.6186327814734252, 0.849209341093155, 0.7903839677394705, 0.5296053232288097, 0.8416668624371814]
mode=send/recv, rank=2, size=8, role=receiver, data=[0.6186327814734252, 0.849209341093155, 0.7903839677394705, 0.5296053232288097, 0.8416668624371814]
mode=send/recv, rank=4, size=8, role=receiver, data=[0.6186327814734252, 0.849209341093155, 0.7903839677394705, 0.5296053232288097, 0.8416668624371814]
mode=send/recv, rank=3, size=8, role=receiver, data=[0.6186327814734252, 0.849209341093155, 0.7903839677394705, 0.5296053232288097, 0.8416668624371814]
mode=send/recv, rank=5, size=8, role=receiver, data=[0.6186327814734252, 0.849209341093155, 0.7903839677394705, 0.5296053232288097, 0.8416668624371814]
mode=send/recv, rank=1, size=8, role=receiver, data=[0.6186327814734252, 0.849209341093155, 0.7903839677394705, 0.5296053232288097, 0.8416668624371814]
mode=send/recv, rank=6, size=8, role=receiver, data=[0.6186327814734252, 0.849209341093155, 0.7903839677394705, 0.5296053232288097, 0.8416668624371814]
mode=send/recv, rank=7, size=8, role=receiver, data=[0.6186327814734252, 0.849209341093155, 0.7903839677394705, 0.5296053232288097, 0.8416668624371814]
*/
