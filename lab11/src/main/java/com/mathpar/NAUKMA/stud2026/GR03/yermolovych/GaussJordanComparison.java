package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import mpi.Intracomm;
import mpi.MPI;
import mpi.MPIException;

public class GaussJordanComparison {
    private enum Mode { DIAGONAL, ALL_GATHER, ROW_COLUMN }

    private record Result(double[] solution, double seconds, List<Integer> rows,
                          List<Integer> columns, double[] pivots) { }

    private boolean reportProgress;
    private final int order;
    private final int n;
    private final int k;
    private final int rank;
    private final int blockRow;
    private final int blockColumn;
    private final int width;
    private final Intracomm rowComm;
    private final Intracomm columnComm;

    private GaussJordanComparison(int order, int n) throws MPIException {
        this.order = order;
        this.n = n;
        k = order / n;
        rank = MPI.COMM_WORLD.getRank();
        blockRow = rank / n;
        blockColumn = rank % n;
        width = k + (blockColumn == n - 1 ? 1 : 0);
        rowComm = MPI.COMM_WORLD.split(blockRow, blockColumn);
        columnComm = MPI.COMM_WORLD.split(blockColumn, blockRow);
    }

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
        int processes = MPI.COMM_WORLD.getSize();
        int n = (int) Math.sqrt(processes);
        String experiment;
        int order;
        long seed;
        int repeats;
        try {
            if (args.length > 4) {
                throw new IllegalArgumentException();
            }
            experiment = args.length > 0 ? args[0] : "demo";
            if (!List.of("accuracy", "time", "demo").contains(experiment)) {
                throw new IllegalArgumentException();
            }
            order = args.length > 1 ? Integer.parseInt(args[1]) : (experiment.equals("demo") ? 4 : 2048);
            seed = args.length > 2 ? Long.parseLong(args[2]) : 2026L;
            repeats = args.length > 3 ? Integer.parseInt(args[3]) : (experiment.equals("time") ? 5 : 1);
            if (n * n != processes || order < 1 || order % n != 0 || repeats < 1 || repeats > 20
                    || !experiment.equals("time") && repeats != 1
                    || experiment.equals("accuracy") && (order != 2048 || processes != 64)
                    || experiment.equals("time") && processes != 4 && processes != 9 && processes != 16) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            if (MPI.COMM_WORLD.getRank() == 0) {
                System.err.println("Usage: GaussJordanComparison [accuracy|time|demo] [N] [seed] [repeats]");
                System.err.println("Use n*n processes and N divisible by n. Accuracy: N=2048, 64 processes, one run.");
                System.err.println("Time: 4, 9 or 16 processes on separate physical cores; repeats: 1..20.");
            }
            return 2;
        }
        GaussJordanComparison solver = new GaussJordanComparison(order, n);
        solver.reportProgress = experiment.equals("accuracy");
        try {
            return solver.execute(experiment, seed, repeats);
        } finally {
            solver.rowComm.free();
            solver.columnComm.free();
        }
    }

    private int execute(String experiment, long seed, int repeats) throws MPIException {
        boolean measureTime = experiment.equals("time");
        int warmups = measureTime ? 5 : 0;
        double[][] original = null;
        if (rank == 0) {
            original = new double[order][order + 1];
            Random random = new Random(seed);
            for (double[] row : original) {
                for (int j = 0; j <= order; j++) {
                    row[j] = 1.0 + 99.0 * random.nextDouble();
                }
            }
            System.out.println("Laboratory 11: comparison of laboratories 9 and 10");
            System.out.println("Experiment: " + experiment);
            System.out.printf("Processes: %d; grid: %d x %d%n", n * n, n, n);
            System.out.printf("A: %d x %d; [A | B]: %d x %d%n", order, order, order, order + 1);
            System.out.printf("N = %d; k = %d; seed = %d%n", order, k, seed);
            System.out.printf("Row communicators: %d; column communicators: %d%n", n, n);
            System.out.printf("Block: %d x %d; last block column: %d x %d%n", k, k, k, k + 1);
            System.out.printf("Measured runs: %d; warm-up runs per method: %d%n", repeats, warmups);
            if (order <= 12) {
                System.out.println("[A | B]:");
                for (double[] row : original) {
                    System.out.println(Arrays.toString(row));
                }
            }
        }
        double[] initialBlock = distribute(original);
        Mode[] modes = Mode.values();
        double[][] times = new double[modes.length][repeats];
        double[][] errors = new double[modes.length][repeats];
        for (int i = 0; i < modes.length; i++) {
            Arrays.fill(times[i], Double.NaN);
            Arrays.fill(errors[i], Double.NaN);
        }
        for (int warmup = 0; warmup < warmups; warmup++) {
            for (int offset = 0; offset < modes.length; offset++) {
                Mode mode = modes[(warmup + offset) % modes.length];
                try {
                    solve(initialBlock, mode);
                } catch (ArithmeticException exception) {
                    if (rank == 0) {
                        System.err.println("Warm-up failed for " + mode + ": " + exception.getMessage());
                    }
                }
            }
            if (rank == 0) {
                System.out.printf("Warm-up round %d/%d completed%n", warmup + 1, warmups);
            }
        }
        if (rank == 0 && measureTime) {
            System.out.println("Run  Method             Solve time (s)       Max residual");
        }
        int exitCode = 0;
        for (int repeat = 0; repeat < repeats; repeat++) {
            for (int offset = 0; offset < modes.length; offset++) {
                Mode mode = modes[(repeat + offset) % modes.length];
                Result result;
                try {
                    result = solve(initialBlock, mode);
                } catch (ArithmeticException exception) {
                    if (rank == 0) {
                        System.err.println("Method " + mode + " failed: " + exception.getMessage());
                    }
                    exitCode = 1;
                    continue;
                }
                times[mode.ordinal()][repeat] = result.seconds();
                int[] status = {0};
                if (rank == 0) {
                    double[] residual = new double[order];
                    double error = 0.0;
                    for (int i = 0; i < order; i++) {
                        double sum = 0.0;
                        for (int j = 0; j < order; j++) {
                            sum += original[i][j] * result.solution()[j];
                        }
                        residual[i] = sum - original[i][order];
                        error = Math.max(error, Math.abs(residual[i]));
                    }
                    errors[mode.ordinal()][repeat] = error;
                    if (measureTime) {
                        System.out.printf(Locale.ROOT, "%3d  %-16s %14.6f %18.12e%n",
                                repeat + 1, mode, result.seconds(), error);
                    } else {
                        System.out.println("Method: " + mode);
                        System.out.printf(Locale.ROOT, "Error = max(abs(A * X - B)) = %.12e%n", error);
                    }
                    if (order <= 12) {
                        System.out.println("Pivot rows: " + result.rows());
                        System.out.println("Pivot columns: " + result.columns());
                        System.out.println("X = " + Arrays.toString(result.solution()));
                        System.out.println("P = A * X - B = " + Arrays.toString(residual));
                    }
                    if (!Double.isFinite(error)) {
                        status[0] = 1;
                        System.err.println("The solution contains non-finite values.");
                    }
                }
                MPI.COMM_WORLD.bcast(status, 1, MPI.INT, 0);
                exitCode = Math.max(exitCode, status[0]);
            }
        }
        if (rank == 0 && measureTime) {
            System.out.println("Median solve time (s) and maximum residual across runs:");
            for (Mode mode : modes) {
                double[] values = times[mode.ordinal()].clone();
                Arrays.sort(values);
                double maxError = Arrays.stream(errors[mode.ordinal()]).max().orElse(Double.NaN);
                if (!Double.isFinite(values[repeats - 1]) || !Double.isFinite(maxError)) {
                    System.out.println(mode + ": unavailable");
                } else {
                    double median = (values[(repeats - 1) / 2] + values[repeats / 2]) / 2.0;
                    System.out.printf(Locale.ROOT, "%-16s %.6f s; error = %.12e%n", mode, median, maxError);
                }
            }
        }
        return exitCode;
    }

    private double[] distribute(double[][] original) throws MPIException {
        double[] local = new double[k * width];
        if (rank == 0) {
            for (int destination = 0; destination < n * n; destination++) {
                int rowStart = (destination / n) * k;
                int columnStart = (destination % n) * k;
                int blockWidth = k + (destination % n == n - 1 ? 1 : 0);
                double[] data = new double[k * blockWidth];
                for (int i = 0; i < k; i++) {
                    System.arraycopy(original[rowStart + i], columnStart, data, i * blockWidth, blockWidth);
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

    private Result solve(double[] initialBlock, Mode mode) throws MPIException {
        double[] block = initialBlock.clone();
        List<Integer> remainingRows = new ArrayList<>();
        List<Integer> remainingColumns = new ArrayList<>();
        List<Integer> pivotRows = new ArrayList<>();
        List<Integer> pivotColumns = new ArrayList<>();
        for (int i = 0; i < order; i++) {
            remainingRows.add(i);
            remainingColumns.add(i);
        }
        boolean[] usedRows = new boolean[order];
        boolean[] usedColumns = new boolean[order];
        double[] pivotValues = new double[order];
        double[] factors = new double[k];
        double[] pivotRow = new double[width];
        MPI.COMM_WORLD.barrier();
        double start = MPI.wtime();
        double[] localBest = mode == Mode.DIAGONAL ? null : findLocalMaximum(block);
        for (int step = 0; step < order; step++) {
            double[] pivot = mode == Mode.DIAGONAL
                    ? diagonalPivot(block, step) : selectPivot(localBest, mode);
            int globalRow = (int) pivot[2];
            int globalColumn = (int) pivot[3];
            if (globalRow < 0 || !Double.isFinite(pivot[1]) || Math.abs(pivot[1]) <= 1.0e-12) {
                throw new ArithmeticException("No usable pivot at step " + step + ": " + pivot[1]);
            }
            remainingRows.remove(Integer.valueOf(globalRow));
            remainingColumns.remove(Integer.valueOf(globalColumn));
            pivotRows.add(globalRow);
            pivotColumns.add(globalColumn);
            usedRows[globalRow] = true;
            usedColumns[globalColumn] = true;
            pivotValues[step] = pivot[1];
            int ownerRow = globalRow / k;
            int ownerColumn = globalColumn / k;
            int localRow = globalRow % k;
            int localColumn = globalColumn % k;
            if (blockColumn == ownerColumn) {
                for (int i = 0; i < k; i++) {
                    factors[i] = block[i * width + localColumn];
                }
            }
            rowComm.bcast(factors, k, MPI.DOUBLE, ownerColumn);
            if (blockRow == ownerRow) {
                for (int j = 0; j < width; j++) {
                    int index = localRow * width + j;
                    block[index] /= pivot[1];
                    pivotRow[j] = block[index];
                }
            }
            columnComm.bcast(pivotRow, width, MPI.DOUBLE, ownerRow);
            localBest = emptyCandidate();
            for (int i = 0; i < k; i++) {
                int row = blockRow * k + i;
                if (row == globalRow) {
                    continue;
                }
                for (int j = 0; j < width; j++) {
                    int index = i * width + j;
                    block[index] -= factors[i] * pivotRow[j];
                    int column = blockColumn * k + j;
                    if (mode != Mode.DIAGONAL && j < k && !usedRows[row] && !usedColumns[column]) {
                        consider(localBest, block[index], row, column);
                    }
                }
                if (blockColumn == ownerColumn) {
                    block[i * width + localColumn] = 0.0;
                }
            }
            if (reportProgress && rank == 0 && (step + 1) % 512 == 0) {
                System.out.printf("%s: %d/%d pivot steps completed%n", mode, step + 1, order);
            }
        }
        double[] byRow = collectRightHandSide(block);
        double[] solution = new double[order];
        if (rank == 0) {
            for (int step = 0; step < order; step++) {
                solution[pivotColumns.get(step)] = byRow[pivotRows.get(step)];
            }
        }
        double[] localTime = {MPI.wtime() - start};
        double[] maxTime = new double[1];
        MPI.COMM_WORLD.allReduce(localTime, maxTime, 1, MPI.DOUBLE, MPI.MAX);
        return new Result(solution, maxTime[0], pivotRows, pivotColumns, pivotValues);
    }

    private double[] findLocalMaximum(double[] block) {
        double[] best = emptyCandidate();
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                consider(best, block[i * width + j], blockRow * k + i, blockColumn * k + j);
            }
        }
        return best;
    }

    private double[] diagonalPivot(double[] block, int step) throws MPIException {
        double[] pivot = emptyCandidate();
        int owner = step / k;
        if (blockRow == owner) {
            if (blockColumn == owner) {
                consider(pivot, block[(step % k) * width + step % k], step, step);
            }
            rowComm.bcast(pivot, 4, MPI.DOUBLE, owner);
        }
        columnComm.bcast(pivot, 4, MPI.DOUBLE, owner);
        return pivot;
    }

    private double[] selectPivot(double[] local, Mode mode) throws MPIException {
        if (mode == Mode.ALL_GATHER) {
            double[] candidates = new double[4 * n * n];
            MPI.COMM_WORLD.allGather(local, 4, MPI.DOUBLE, candidates, 4, MPI.DOUBLE);
            return bestOf(candidates);
        }
        double[] rowCandidates = new double[4 * n];
        rowComm.gather(local, 4, MPI.DOUBLE, rowCandidates, 4, MPI.DOUBLE, n - 1);
        double[] best = emptyCandidate();
        if (blockColumn == n - 1) {
            double[] rowBest = bestOf(rowCandidates);
            double[] columnCandidates = new double[4 * n];
            columnComm.allGather(rowBest, 4, MPI.DOUBLE, columnCandidates, 4, MPI.DOUBLE);
            best = bestOf(columnCandidates);
        }
        rowComm.bcast(best, 4, MPI.DOUBLE, n - 1);
        return best;
    }

    private static double[] emptyCandidate() {
        return new double[]{-1.0, 0.0, -1.0, -1.0};
    }

    private static void consider(double[] best, double value, int row, int column) {
        double magnitude = Double.isFinite(value) ? Math.abs(value) : Double.POSITIVE_INFINITY;
        if (magnitude > best[0] || magnitude == best[0]
                && (row < best[2] || row == best[2] && column < best[3])) {
            best[0] = magnitude;
            best[1] = value;
            best[2] = row;
            best[3] = column;
        }
    }

    private static double[] bestOf(double[] candidates) {
        double[] best = emptyCandidate();
        for (int i = 0; i < candidates.length; i += 4) {
            if (candidates[i + 2] >= 0) {
                consider(best, candidates[i + 1], (int) candidates[i + 2], (int) candidates[i + 3]);
            }
        }
        return best;
    }

    private double[] collectRightHandSide(double[] block) throws MPIException {
        double[] byRow = new double[order];
        if (blockColumn == n - 1) {
            double[] part = new double[k];
            for (int i = 0; i < k; i++) {
                part[i] = block[i * width + k];
            }
            columnComm.gather(part, k, MPI.DOUBLE, byRow, k, MPI.DOUBLE, 0);
            if (rank == n - 1 && rank != 0) {
                MPI.COMM_WORLD.send(byRow, order, MPI.DOUBLE, 0, 20);
            }
        }
        if (rank == 0 && n > 1) {
            MPI.COMM_WORLD.recv(byRow, order, MPI.DOUBLE, n - 1, 20);
        }
        return byRow;
    }
}

/*
Run:
cd ~/paralel
source scripts/env.sh
mvn -q -o -Dlab.directory=lab11 compile
mpirun --oversubscribe --bind-to none --mca mpi_yield_when_idle 1 --timeout 1800 -np 64 java -Xms64m -Xmx256m -XX:ActiveProcessorCount=1 -XX:+UseSerialGC -XX:-UsePerfData -cp lab11/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.GaussJordanComparison accuracy 2048 2026 1
Terminal output:
Laboratory 11: comparison of laboratories 9 and 10
Experiment: accuracy
Processes: 64; grid: 8 x 8
A: 2048 x 2048; [A | B]: 2048 x 2049
N = 2048; k = 256; seed = 2026
Row communicators: 8; column communicators: 8
Block: 256 x 256; last block column: 256 x 257
Measured runs: 1; warm-up runs per method: 0
DIAGONAL: 512/2048 pivot steps completed
DIAGONAL: 1024/2048 pivot steps completed
DIAGONAL: 1536/2048 pivot steps completed
DIAGONAL: 2048/2048 pivot steps completed
Method: DIAGONAL
Error = max(abs(A * X - B)) = 8.255898528375e-06
ALL_GATHER: 512/2048 pivot steps completed
ALL_GATHER: 1024/2048 pivot steps completed
ALL_GATHER: 1536/2048 pivot steps completed
ALL_GATHER: 2048/2048 pivot steps completed
Method: ALL_GATHER
Error = max(abs(A * X - B)) = 1.738058585943e-10
ROW_COLUMN: 512/2048 pivot steps completed
ROW_COLUMN: 1024/2048 pivot steps completed
ROW_COLUMN: 1536/2048 pivot steps completed
ROW_COLUMN: 2048/2048 pivot steps completed
Method: ROW_COLUMN
Error = max(abs(A * X - B)) = 1.738058585943e-10
*/

/*
Run:
cd ~/paralel
source scripts/env.sh
mvn -q -o -Dlab.directory=lab11 compile
mkdir -p lab11/results
mpirun --nooversubscribe --bind-to core --map-by core --report-bindings --timeout 1800 -np 4 java -Xms64m -Xmx256m -XX:ActiveProcessorCount=1 -XX:+UseSerialGC -XX:-UsePerfData -cp lab11/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.GaussJordanComparison time 2048 2026 5 2> lab11/results/time-bindings.txt
Terminal output:
Laboratory 11: comparison of laboratories 9 and 10
Experiment: time
Processes: 4; grid: 2 x 2
A: 2048 x 2048; [A | B]: 2048 x 2049
N = 2048; k = 1024; seed = 2026
Row communicators: 2; column communicators: 2
Block: 1024 x 1024; last block column: 1024 x 1025
Measured runs: 5; warm-up runs per method: 5
Warm-up round 1/5 completed
Warm-up round 2/5 completed
Warm-up round 3/5 completed
Warm-up round 4/5 completed
Warm-up round 5/5 completed
Run  Method             Solve time (s)       Max residual
  1  DIAGONAL               5.995133 8.255898528375e-06
  1  ALL_GATHER             9.684816 1.738058585943e-10
  1  ROW_COLUMN             8.458553 1.738058585943e-10
  2  ALL_GATHER             8.254106 1.738058585943e-10
  2  ROW_COLUMN             8.193955 1.738058585943e-10
  2  DIAGONAL               5.017753 8.255898528375e-06
  3  ROW_COLUMN             8.406051 1.738058585943e-10
  3  DIAGONAL               5.213257 8.255898528375e-06
  3  ALL_GATHER             8.871788 1.738058585943e-10
  4  DIAGONAL               5.244522 8.255898528375e-06
  4  ALL_GATHER             8.626563 1.738058585943e-10
  4  ROW_COLUMN            10.179038 1.738058585943e-10
  5  ALL_GATHER             9.505061 1.738058585943e-10
  5  ROW_COLUMN            10.374450 1.738058585943e-10
  5  DIAGONAL               5.724543 8.255898528375e-06
Median solve time (s) and maximum residual across runs:
DIAGONAL         5.244522 s; error = 8.255898528375e-06
ALL_GATHER       8.871788 s; error = 1.738058585943e-10
ROW_COLUMN       8.458553 s; error = 1.738058585943e-10
*/
