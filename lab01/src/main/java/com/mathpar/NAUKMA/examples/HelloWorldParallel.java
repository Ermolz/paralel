package com.mathpar.NAUKMA.examples;

import mpi.*;

public class HelloWorldParallel {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        int myrank = MPI.COMM_WORLD.getRank();
        System.out.println("Proc num " + myrank + " Hello World");
        MPI.Finalize();
    }
}
