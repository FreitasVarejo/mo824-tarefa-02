import java.io.*;
import java.nio.file.*;
import java.util.*;
import metaheuristics.grasp.AbstractGRASP;
import problems.scqbf.solvers.GRASP_SCQBF;
import problems.scqbf.solvers.GRASP_SCQBF.ConstructionMode;
import problems.scqbf.solvers.GRASP_SCQBF.LocalSearchType;
import solutions.Solution;

public class RunnerSCQBF {

    static class Config {
        final String name;
        final ConstructionMode mode;
        final LocalSearchType ls;
        final double alpha;
        final int sampleP;
        final double[] reactiveAlphas;
        final int reactiveBlock;
        Config(String name, ConstructionMode mode, LocalSearchType ls, double alpha,
               int sampleP, double[] reactiveAlphas, int reactiveBlock) {
            this.name = name; this.mode = mode; this.ls = ls; this.alpha = alpha;
            this.sampleP = sampleP; this.reactiveAlphas = reactiveAlphas; this.reactiveBlock = reactiveBlock;
        }
    }

    static List<Path> loadInstances(String path) throws IOException {
        Path p = Paths.get(path);
        List<Path> insts = new ArrayList<>();
        if (Files.isDirectory(p)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
                for (Path f : ds) if (Files.isRegularFile(f)) insts.add(f);
            }
        } else { // trata como arquivo-lista
            try (BufferedReader br = Files.newBufferedReader(p)) {
                String line;
                while ((line = br.readLine()) != null) {
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#")) continue;
                    Path f = Paths.get(s);
                    insts.add(f.isAbsolute() ? f : p.getParent().resolve(f));
                }
            }
        }
        insts.sort(Comparator.comparing(x -> x.getFileName().toString()));
        return insts;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Uso: java RunnerSCQBF <dir_ou_arquivo-lista> <saida.csv> [minutos=30] [seed=42]");
            System.exit(1);
        }
        String src = args[0];
        String outCsv  = args[1];
        double minutes = (args.length >= 3 ? Double.parseDouble(args[2]) : 30.0);
        long seed = (args.length >= 4 ? Long.parseLong(args[3]) : 42L);

        // configura seed global do framework
        AbstractGRASP.setGlobalSeed(seed);

        double seconds = minutes * 60.0;

        // 5 configs pedidas
        double alpha1 = 0.20, alpha2 = 0.60;
        List<Config> cfgs = List.of(
            new Config("STD_a0.20_FIRST", ConstructionMode.STANDARD, LocalSearchType.FIRST_IMPROVING, alpha1, 0, null, 0),
            new Config("STD_a0.60_FIRST", ConstructionMode.STANDARD, LocalSearchType.FIRST_IMPROVING, alpha2, 0, null, 0),
            new Config("STD_a0.20_BEST",  ConstructionMode.STANDARD, LocalSearchType.BEST_IMPROVING,  alpha1, 0, null, 0),
            new Config("SAMPLED_p64_FIRST", ConstructionMode.SAMPLED, LocalSearchType.FIRST_IMPROVING, alpha1, 64, null, 0),
            new Config("REACTIVE_FIRST", ConstructionMode.REACTIVE, LocalSearchType.FIRST_IMPROVING, alpha1,
                       0, new double[]{0.10,0.20,0.30,0.40,0.50}, 20)
        );

        List<Path> insts = loadInstances(src);

        try (PrintWriter pw = new PrintWriter(new FileWriter(outCsv))) {
            pw.println("instance,config,alpha,mode,ls,best_f,time_s,time_to_best_s,iters,best_iter,seed");
            for (Path inst : insts) {
                String fname = inst.toString();
                for (Config cfg : cfgs) {
                    long t0 = System.nanoTime();
                    int iterations = Integer.MAX_VALUE;

                    GRASP_SCQBF grasp = new GRASP_SCQBF(
                        cfg.alpha, iterations, fname,
                        cfg.mode, cfg.ls,
                        cfg.sampleP, cfg.reactiveAlphas, cfg.reactiveBlock
                    );
                    GRASP_SCQBF.verbose = false;
                    grasp.setTimeLimitSeconds(seconds);

                    Solution<Integer> best = grasp.solve();

                    long t1 = System.nanoTime();
                    double bestF = -best.cost;
                    double elapsed = (t1 - t0) / 1e9;

                    pw.printf("%s,%s,%.2f,%s,%s,%.6f,%.3f,%.3f,%d,%d,%d%n",
                        inst.getFileName().toString(), cfg.name, cfg.alpha, cfg.mode, cfg.ls,
                        bestF, elapsed, grasp.bestTimeSec, grasp.iterationsRun, grasp.bestIter, seed
                    );
                    pw.flush();

                    System.out.printf("OK: %s | %s | f=%.6f | best@%ds (it %d) | t=%.0fs%n",
                        inst.getFileName(), cfg.name, bestF,
                        Math.round(grasp.bestTimeSec), grasp.bestIter, Math.round(elapsed)
                    );
                }
            }
        }
        System.out.println("Resultados salvos em: " + outCsv);
    }
}
