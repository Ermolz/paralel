package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import mpi.Intracomm;
import mpi.MPI;
import mpi.MPIException;

public class GaussJordan {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        int exitCode;
        try {
            exitCode = run(args);
        } finally {
            MPI.Finalize();
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static int run(String[] args) throws MPIException {
        int rank = MPI.COMM_WORLD.getRank();
        int processes = MPI.COMM_WORLD.getSize();
        int n = (int) Math.sqrt(processes);
        int order;
        long seed;
        try {
            if (args.length > 2) {
                throw new IllegalArgumentException();
            }
            order = args.length > 0 ? Integer.parseInt(args[0]) : 4;
            seed = args.length > 1 ? Long.parseLong(args[1]) : 2026L;
            if (n * n != processes || order < 1 || order % n != 0) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            if (rank == 0) {
                System.err.println("Usage: GaussJordan [N] [seed]");
                System.err.println("Process count must be n*n; N must be positive and divisible by n.");
            }
            return 2;
        }

        int k = order / n;
        int blockRow = rank / n;
        int blockColumn = rank % n;
        int width = k + (blockColumn == n - 1 ? 1 : 0);
        Intracomm rowComm = MPI.COMM_WORLD.split(blockRow, blockColumn);
        Intracomm columnComm = MPI.COMM_WORLD.split(blockColumn, blockRow);
        try {
            double[][] original = null;
            if (rank == 0) {
                original = new double[order][order + 1];
                Random random = new Random(seed);
                for (double[] row : original) {
                    for (int j = 0; j <= order; j++) {
                        row[j] = 1.0 + 99.0 * random.nextDouble();
                    }
                }
                System.out.println("Gauss-Jordan elimination without pivoting");
                System.out.printf("Processes: %d; grid: %d x %d%n", processes, n, n);
                System.out.printf("N = %d; k = %d; seed = %d%n", order, k, seed);
                System.out.printf("Row communicators: %d, size: %d%n", n, rowComm.getSize());
                System.out.printf("Column communicators: %d, size: %d%n", n, columnComm.getSize());
                System.out.printf("Block: %d x %d; last block column: %d x %d%n", k, k, k, k + 1);
                if (order <= 12) {
                    System.out.println("[A | B]:");
                    for (double[] row : original) {
                        System.out.println(Arrays.toString(row));
                    }
                }
            }

            double[] block = distribute(original, order, n, k, width, rank);
            if (!eliminate(block, order, k, width, blockRow, blockColumn, rowComm, columnComm)) {
                return 1;
            }
            double[] solution = collectSolution(block, order, n, k, width, blockColumn, rank, columnComm);
            int[] status = {0};
            if (rank == 0) {
                double[] residual = new double[order];
                double error = 0.0;
                for (int i = 0; i < order; i++) {
                    double sum = 0.0;
                    for (int j = 0; j < order; j++) {
                        sum += original[i][j] * solution[j];
                    }
                    residual[i] = sum - original[i][order];
                    error = Math.max(error, Math.abs(residual[i]));
                }
                if (order <= 12) {
                    System.out.println("X = " + Arrays.toString(solution));
                    System.out.println("P = A * X - B = " + Arrays.toString(residual));
                }
                System.out.printf(Locale.ROOT, "Error = max(abs(P[i])) = %.12e%n", error);
                if (!Double.isFinite(error)) {
                    System.err.println("The solution contains non-finite values.");
                    status[0] = 1;
                }
            }
            MPI.COMM_WORLD.bcast(status, 1, MPI.INT, 0);
            return status[0];
        } finally {
            rowComm.free();
            columnComm.free();
        }
    }

    private static double[] distribute(double[][] original, int order, int n, int k,
                                       int width, int rank) throws MPIException {
        double[] local = new double[k * width];
        if (rank == 0) {
            for (int destination = 0; destination < n * n; destination++) {
                int rowStart = (destination / n) * k;
                int columnStart = (destination % n) * k;
                int blockWidth = k + (destination % n == n - 1 ? 1 : 0);
                double[] data = new double[k * blockWidth];
                for (int i = 0; i < k; i++) {
                    System.arraycopy(original[rowStart + i], columnStart, data, i * blockWidth, k);
                    if (blockWidth > k) {
                        data[i * blockWidth + k] = original[rowStart + i][order];
                    }
                }
                if (destination == 0) {
                    local = data;
                } else {
                    MPI.COMM_WORLD.send(data, data.length, MPI.DOUBLE, destination, 10);
                }
            }
        } else {
            MPI.COMM_WORLD.recv(local, local.length, MPI.DOUBLE, 0, 10);
        }
        return local;
    }

    private static boolean eliminate(double[] block, int order, int k, int width,
                                     int blockRow, int blockColumn,
                                     Intracomm rowComm, Intracomm columnComm) throws MPIException {
        double[] pivot = new double[1];
        double[] factors = new double[k];
        double[] pivotRow = new double[width];
        for (int step = 0; step < order; step++) {
            int owner = step / k;
            int localIndex = step % k;
            if (blockRow == owner) {
                if (blockColumn == owner) {
                    pivot[0] = block[localIndex * width + localIndex];
                }
                rowComm.bcast(pivot, 1, MPI.DOUBLE, owner);
            }
            columnComm.bcast(pivot, 1, MPI.DOUBLE, owner);
            if (!Double.isFinite(pivot[0]) || Math.abs(pivot[0]) <= 1.0e-12) {
                if (MPI.COMM_WORLD.getRank() == 0) {
                    System.err.printf(Locale.ROOT, "Cannot use diagonal pivot at step %d: %.12e%n", step, pivot[0]);
                }
                return false;
            }

            if (blockColumn == owner) {
                for (int i = 0; i < k; i++) {
                    factors[i] = block[i * width + localIndex];
                }
            }
            rowComm.bcast(factors, k, MPI.DOUBLE, owner);
            if (blockRow == owner) {
                for (int j = 0; j < width; j++) {
                    int index = localIndex * width + j;
                    block[index] /= pivot[0];
                    pivotRow[j] = block[index];
                }
            }
            columnComm.bcast(pivotRow, width, MPI.DOUBLE, owner);
            for (int i = 0; i < k; i++) {
                if (blockRow * k + i == step) {
                    continue;
                }
                for (int j = 0; j < width; j++) {
                    block[i * width + j] -= factors[i] * pivotRow[j];
                }
                if (blockColumn == owner) {
                    block[i * width + localIndex] = 0.0;
                }
            }
        }
        return true;
    }

    private static double[] collectSolution(double[] block, int order, int n, int k,
                                           int width, int blockColumn, int rank,
                                           Intracomm columnComm) throws MPIException {
        double[] solution = new double[order];
        if (blockColumn == n - 1) {
            double[] part = new double[k];
            for (int i = 0; i < k; i++) {
                part[i] = block[i * width + k];
            }
            columnComm.gather(part, k, MPI.DOUBLE, solution, k, MPI.DOUBLE, 0);
            if (rank == n - 1 && rank != 0) {
                MPI.COMM_WORLD.send(solution, order, MPI.DOUBLE, 0, 20);
            }
        }
        if (rank == 0 && n > 1) {
            MPI.COMM_WORLD.recv(solution, order, MPI.DOUBLE, n - 1, 20);
        }
        return solution;
    }
}

/*
Run:
cd ~/paralel
source scripts/env.sh
mvn -q -o -Dlab.directory=lab09 compile
mpirun --oversubscribe --bind-to none --timeout 120 -np 4 java -cp lab09/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.GaussJordan 4 2026
Terminal output:
Gauss-Jordan elimination without pivoting
Processes: 4; grid: 2 x 2
N = 4; k = 2; seed = 2026
Row communicators: 2, size: 2
Column communicators: 2, size: 2
Block: 2 x 2; last block column: 2 x 3
[A | B]:
[62.24464536586909, 85.07172476822235, 79.24801280620758, 53.43092699965216, 84.32501938128095]
[15.68130504283984, 84.53444528178413, 58.89096416605603, 17.505986044187857, 71.86264432303511]
[54.3897346864111, 28.66983357189241, 6.509429460442563, 2.2955666722262498, 36.42394095084474]
[75.24683145921551, 58.28416672016343, 25.966831374694372, 54.233647734455744, 54.76796179329035]
X = [0.31333388158829834, 0.6245717148608959, 0.31348223645227696, -0.24619775496185872]
P = A * X - B = [0.0, 0.0, -7.105427357601002E-15, -7.105427357601002E-15]
Error = max(abs(P[i])) = 7.105427357601e-15
*/
