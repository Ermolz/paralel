package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import mpi.Intracomm;
import mpi.MPI;
import mpi.MPIException;

public class GaussJordanFullPivot {
    private enum Mode { DIAGONAL, ALL_GATHER, ROW_COLUMN }

    private record Result(double[] solution, double seconds, List<Integer> rows,
                          List<Integer> columns, double[] pivots) { }

    private final int order;
    private final int n;
    private final int k;
    private final int rank;
    private final int blockRow;
    private final int blockColumn;
    private final int width;
    private final Intracomm rowComm;
    private final Intracomm columnComm;

    private GaussJordanFullPivot(int order, int n) throws MPIException {
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
        int n = (int) Math.sqrt(MPI.COMM_WORLD.getSize());
        int order;
        long seed;
        int repeats;
        try {
            if (args.length > 3) {
                throw new IllegalArgumentException();
            }
            order = args.length > 0 ? Integer.parseInt(args[0]) : 4;
            seed = args.length > 1 ? Long.parseLong(args[1]) : 2026L;
            repeats = args.length > 2 ? Integer.parseInt(args[2]) : 1;
            if (n * n != MPI.COMM_WORLD.getSize() || order < 1 || order % n != 0
                    || repeats < 1 || repeats > 20) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            if (MPI.COMM_WORLD.getRank() == 0) {
                System.err.println("Usage: GaussJordanFullPivot [N] [seed] [repeats]");
                System.err.println("Process count must be n*n; N must be positive and divisible by n; repeats: 1..20.");
            }
            return 2;
        }
        GaussJordanFullPivot solver = new GaussJordanFullPivot(order, n);
        try {
            return solver.execute(seed, repeats);
        } finally {
            solver.rowComm.free();
            solver.columnComm.free();
        }
    }

    private int execute(long seed, int repeats) throws MPIException {
        int warmups = repeats > 1 ? 5 : 0;
        double[][] original = null;
        if (rank == 0) {
            original = new double[order][order + 1];
            Random random = new Random(seed);
            for (double[] row : original) {
                for (int j = 0; j <= order; j++) {
                    row[j] = 1.0 + 99.0 * random.nextDouble();
                }
            }
            System.out.println("Gauss-Jordan elimination: diagonal and full pivoting");
            System.out.printf("Processes: %d; grid: %d x %d%n", n * n, n, n);
            System.out.printf("N = %d; k = %d; seed = %d%n", order, k, seed);
            System.out.printf("Row communicators: %d, size: %d%n", n, rowComm.getSize());
            System.out.printf("Column communicators: %d, size: %d%n", n, columnComm.getSize());
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
        for (double[] values : times) {
            Arrays.fill(values, Double.NaN);
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
                    System.out.printf("%nMethod: %s; run: %d%n", mode, repeat + 1);
                    if (order <= 12) {
                        System.out.println("Pivot rows: " + result.rows());
                        System.out.println("Pivot columns: " + result.columns());
                        System.out.println("Pivot values: " + Arrays.toString(result.pivots()));
                        int[] owners = new int[order];
                        for (int step = 0; step < order; step++) {
                            owners[step] = (result.rows().get(step) / k) * n + result.columns().get(step) / k;
                        }
                        System.out.println("Pivot ranks: " + Arrays.toString(owners));
                        System.out.println("X = " + Arrays.toString(result.solution()));
                        System.out.println("P = A * X - B = " + Arrays.toString(residual));
                    }
                    System.out.printf(Locale.ROOT, "Error = max(abs(P[i])) = %.12e%n", error);
                    System.out.printf(Locale.ROOT, "Solve time (s): %.6f%n", result.seconds());
                    if (!Double.isFinite(error)) {
                        status[0] = 1;
                        System.err.println("The solution contains non-finite values.");
                    }
                }
                MPI.COMM_WORLD.bcast(status, 1, MPI.INT, 0);
                exitCode = Math.max(exitCode, status[0]);
            }
        }
        if (rank == 0) {
            System.out.println("\nMedian solve time (s):");
            for (Mode mode : modes) {
                double[] values = times[mode.ordinal()].clone();
                Arrays.sort(values);
                if (!Double.isFinite(values[repeats - 1])) {
                    System.out.println(mode + ": unavailable");
                } else {
                    double median = (values[(repeats - 1) / 2] + values[repeats / 2]) / 2.0;
                    System.out.printf(Locale.ROOT, "%s: %.6f%n", mode, median);
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
mvn -q -o -Dlab.directory=lab10 compile
mpirun --nooversubscribe --bind-to core --map-by core --timeout 300 -np 4 java -XX:-UsePerfData -cp lab10/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.GaussJordanFullPivot 4 2026 1
Terminal output:
Gauss-Jordan elimination: diagonal and full pivoting
Processes: 4; grid: 2 x 2
N = 4; k = 2; seed = 2026
Row communicators: 2, size: 2
Column communicators: 2, size: 2
Block: 2 x 2; last block column: 2 x 3
Measured runs: 1; warm-up runs per method: 0
[A | B]:
[62.24464536586909, 85.07172476822235, 79.24801280620758, 53.43092699965216, 84.32501938128095]
[15.68130504283984, 84.53444528178413, 58.89096416605603, 17.505986044187857, 71.86264432303511]
[54.3897346864111, 28.66983357189241, 6.509429460442563, 2.2955666722262498, 36.42394095084474]
[75.24683145921551, 58.28416672016343, 25.966831374694372, 54.233647734455744, 54.76796179329035]

Method: DIAGONAL; run: 1
Pivot rows: [0, 1, 2, 3]
Pivot columns: [0, 1, 2, 3]
Pivot values: [62.24464536586909, 63.102309894560825, -34.567699269669404, 43.29674303608643]
Pivot ranks: [0, 0, 3, 3]
X = [0.31333388158829834, 0.6245717148608959, 0.31348223645227696, -0.24619775496185872]
P = A * X - B = [0.0, 0.0, -7.105427357601002E-15, -7.105427357601002E-15]
Error = max(abs(P[i])) = 7.105427357601e-15
Solve time (s): 0.000347

Method: ALL_GATHER; run: 1
Pivot rows: [0, 1, 3, 2]
Pivot columns: [1, 0, 2, 3]
Pivot values: [85.07172476822235, -46.17022767382861, -42.348592215933465, -35.3416421730419]
Pivot ranks: [0, 0, 3, 3]
X = [0.31333388158829845, 0.6245717148608959, 0.31348223645227713, -0.2461977549618591]
P = A * X - B = [0.0, 1.4210854715202004E-14, 0.0, 0.0]
Error = max(abs(P[i])) = 1.421085471520e-14
Solve time (s): 0.000198

Method: ROW_COLUMN; run: 1
Pivot rows: [0, 1, 3, 2]
Pivot columns: [1, 0, 2, 3]
Pivot values: [85.07172476822235, -46.17022767382861, -42.348592215933465, -35.3416421730419]
Pivot ranks: [0, 0, 3, 3]
X = [0.31333388158829845, 0.6245717148608959, 0.31348223645227713, -0.2461977549618591]
P = A * X - B = [0.0, 1.4210854715202004E-14, 0.0, 0.0]
Error = max(abs(P[i])) = 1.421085471520e-14
Solve time (s): 0.000112

Median solve time (s):
DIAGONAL: 0.000347
ALL_GATHER: 0.000198
ROW_COLUMN: 0.000112
*/

/*
Run:
cd ~/paralel
source scripts/env.sh
mvn -q -o -Dlab.directory=lab10 compile
mkdir -p lab10/results
mpirun --nooversubscribe --bind-to core --map-by core --report-bindings --timeout 300 -np 4 java -XX:-UsePerfData -cp lab10/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.GaussJordanFullPivot 512 2026 5 2> lab10/results/benchmark-bindings.txt
Terminal output:
Gauss-Jordan elimination: diagonal and full pivoting
Processes: 4; grid: 2 x 2
N = 512; k = 256; seed = 2026
Row communicators: 2, size: 2
Column communicators: 2, size: 2
Block: 256 x 256; last block column: 256 x 257
Measured runs: 5; warm-up runs per method: 5

Method: DIAGONAL; run: 1
Error = max(abs(P[i])) = 1.474639077514e-09
Solve time (s): 0.072454

Method: ALL_GATHER; run: 1
Error = max(abs(P[i])) = 2.948752353404e-12
Solve time (s): 0.169141

Method: ROW_COLUMN; run: 1
Error = max(abs(P[i])) = 2.948752353404e-12
Solve time (s): 0.122149

Method: ALL_GATHER; run: 2
Error = max(abs(P[i])) = 2.948752353404e-12
Solve time (s): 0.124186

Method: ROW_COLUMN; run: 2
Error = max(abs(P[i])) = 2.948752353404e-12
Solve time (s): 0.105368

Method: DIAGONAL; run: 2
Error = max(abs(P[i])) = 1.474639077514e-09
Solve time (s): 0.064195

Method: ROW_COLUMN; run: 3
Error = max(abs(P[i])) = 2.948752353404e-12
Solve time (s): 0.119831

Method: DIAGONAL; run: 3
Error = max(abs(P[i])) = 1.474639077514e-09
Solve time (s): 0.090438

Method: ALL_GATHER; run: 3
Error = max(abs(P[i])) = 2.948752353404e-12
Solve time (s): 0.112030

Method: DIAGONAL; run: 4
Error = max(abs(P[i])) = 1.474639077514e-09
Solve time (s): 0.060266

Method: ALL_GATHER; run: 4
Error = max(abs(P[i])) = 2.948752353404e-12
Solve time (s): 0.109525

Method: ROW_COLUMN; run: 4
Error = max(abs(P[i])) = 2.948752353404e-12
Solve time (s): 0.105368

Method: ALL_GATHER; run: 5
Error = max(abs(P[i])) = 2.948752353404e-12
Solve time (s): 0.107863

Method: ROW_COLUMN; run: 5
Error = max(abs(P[i])) = 2.948752353404e-12
Solve time (s): 0.091802

Method: DIAGONAL; run: 5
Error = max(abs(P[i])) = 1.474639077514e-09
Solve time (s): 0.063445

Median solve time (s):
DIAGONAL: 0.064195
ALL_GATHER: 0.112030
ROW_COLUMN: 0.105368
*/
