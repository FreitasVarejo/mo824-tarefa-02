package problems.scqbf.solvers;

import java.io.IOException;
import java.util.*;
import metaheuristics.grasp.AbstractGRASP;
import problems.scqbf.SCQBF_Inverse;
import solutions.Solution;

public class GRASP_SCQBF extends AbstractGRASP<Integer> {

    public enum LocalSearchType { FIRST_IMPROVING, BEST_IMPROVING }
    public enum ConstructionMode { STANDARD, SAMPLED, REACTIVE }

    private final ConstructionMode mode;
    private final LocalSearchType lsType;

    private final int sampleP;

    private final double[] alphas;
    private final int reactiveBlock;
    private double[] probs;
    private double[] avgGain;
    private int[] countAlpha;
    private double bestSoFar;

    private long timeLimitNanos = Long.MAX_VALUE;

    // NEW: métricas para o Runner
    public int iterationsRun = 0;     // iterações efetivamente executadas
    public int bestIter = -1;         // iteração em que o melhor foi encontrado
    public double bestTimeSec = 0.0;  // tempo (s) até o melhor

    public void setTimeLimitSeconds(double seconds) {
        this.timeLimitNanos = (long)(seconds * 1e9);
    }
    public long now() { return System.nanoTime(); }

    public GRASP_SCQBF(
            Double alpha,
            Integer iterations,
            String filename,
            ConstructionMode mode,
            LocalSearchType lsType,
            int sampleP,
            double[] reactiveAlphas,
            int reactiveBlock
    ) throws IOException {
        super(new SCQBF_Inverse(filename), alpha, iterations);
        this.mode = mode;
        this.lsType = lsType;
        this.sampleP = sampleP;
        this.alphas = (reactiveAlphas == null || reactiveAlphas.length == 0) ? new double[]{alpha} : reactiveAlphas;
        this.reactiveBlock = (reactiveBlock <= 0 ? 20 : reactiveBlock);
        this.bestSoFar = Double.NEGATIVE_INFINITY;

        if (mode == ConstructionMode.REACTIVE) {
            int m = this.alphas.length;
            this.probs = new double[m];
            this.avgGain = new double[m];
            this.countAlpha = new int[m];
            Arrays.fill(this.probs, 1.0 / m);
            Arrays.fill(this.avgGain, 0.0);
            Arrays.fill(this.countAlpha, 0);
        }
    }

    /* ----------------------- AbstractGRASP overrides ----------------------- */

    @Override
    public ArrayList<Integer> makeCL() {
        // CL = todos fora da solução (o AbstractGRASP chama antes de construir)
        ArrayList<Integer> _CL = new ArrayList<>();
        for (int i = 0; i < ObjFunction.getDomainSize(); i++) {
            _CL.add(i);
        }
        return _CL;
    }

    @Override
    public ArrayList<Integer> makeRCL() {
        // O AbstractGRASP vai preencher a RCL na constructiveHeuristic padrão.
        return new ArrayList<>();
    }

    @Override
    public void updateCL() {
        // Para STANDARD e REACTIVE: CL = todos os ainda não selecionados (o AbstractGRASP remove os adicionados).
        // Para SAMPLED: reconstruímos a CL como uma AMOSTRA de candidatos fora da solução.
        if (mode == ConstructionMode.SAMPLED) {
            // Reconstrói CL com amostra aleatória de elementos fora da solução:
            HashSet<Integer> inSol = new HashSet<>(sol);
            ArrayList<Integer> all = new ArrayList<>();
            for (int i = 0; i < ObjFunction.getDomainSize(); i++) {
                if (!inSol.contains(i)) all.add(i);
            }
            Collections.shuffle(all, rng);
            CL.clear();
            int p = Math.min(sampleP, all.size());
            for (int k = 0; k < p; k++) CL.add(all.get(k));
        } else {
            // STANDARD/REACTIVE: mantém todos fora de sol como candidatos
            HashSet<Integer> inSol = new HashSet<>(sol);
            CL.removeIf(inSol::contains);
            // garante que CL contém todos fora de sol
            for (int i = 0; i < ObjFunction.getDomainSize(); i++) {
                if (!inSol.contains(i) && !CL.contains(i)) CL.add(i);
            }
        }
    }

    @Override
    public Solution<Integer> createEmptySol() {
        Solution<Integer> s = new Solution<>();
        s.cost = 0.0; // custo = –f, solução vazia => f=0 => custo=0
        return s;
    }

    @Override
    public Solution<Integer> localSearch() {
        // Implementa dois modos: FIRST_IMPROVING e BEST_IMPROVING
        final double EPS = 1e-12;
        boolean improved;
        long tStart = now();

        do {
            if ((now() - tStart) > timeLimitNanos) break;

            improved = false;
            updateCL();

            if (lsType == LocalSearchType.FIRST_IMPROVING) {
                // 1) tentativas de inserção
                Collections.shuffle(CL, rng);
                for (Integer candIn : CL) {
                    double dc = ObjFunction.evaluateInsertionCost(candIn, sol);
                    if (dc < -EPS) {
                        sol.add(candIn);
                        CL.remove(candIn);
                        ObjFunction.evaluate(sol);
                        improved = true;
                        break;
                    }
                }
                if (!improved) {
                    // 2) tentativas de remoção
                    ArrayList<Integer> inside = new ArrayList<>(sol);
                    Collections.shuffle(inside, rng);
                    for (Integer candOut : inside) {
                        double dc = ObjFunction.evaluateRemovalCost(candOut, sol);
                        if (dc < -EPS) {
                            sol.remove(candOut);
                            CL.add(candOut);
                            ObjFunction.evaluate(sol);
                            improved = true;
                            break;
                        }
                    }
                }
                if (!improved) {
                    // 3) troca (opcional): só tenta se add/drop não melhoram
                    ArrayList<Integer> inside = new ArrayList<>(sol);
                    Collections.shuffle(inside, rng);
                    Collections.shuffle(CL, rng);
                    outer:
                    for (Integer candIn : CL) {
                        for (Integer candOut : inside) {
                            double dc = ObjFunction.evaluateExchangeCost(candIn, candOut, sol);
                            if (dc < -EPS) {
                                sol.remove(candOut);
                                CL.add(candOut);
                                sol.add(candIn);
                                CL.remove(candIn);
                                ObjFunction.evaluate(sol);
                                improved = true;
                                break outer;
                            }
                        }
                    }
                }
            } else {
                // BEST_IMPROVING
                double bestDc = -EPS;
                Integer bestIn = null, bestOut = null;
                // inserções
                for (Integer candIn : CL) {
                    double dc = ObjFunction.evaluateInsertionCost(candIn, sol);
                    if (dc < bestDc) {
                        bestDc = dc; bestIn = candIn; bestOut = null;
                    }
                }
                // remoções
                for (Integer candOut : sol) {
                    double dc = ObjFunction.evaluateRemovalCost(candOut, sol);
                    if (dc < bestDc) {
                        bestDc = dc; bestIn = null; bestOut = candOut;
                    }
                }
                // trocas
                for (Integer candIn : CL) {
                    for (Integer candOut : sol) {
                        double dc = ObjFunction.evaluateExchangeCost(candIn, candOut, sol);
                        if (dc < bestDc) {
                            bestDc = dc; bestIn = candIn; bestOut = candOut;
                        }
                    }
                }
                if (bestIn != null || bestOut != null) {
                    if (bestOut != null) {
                        sol.remove(bestOut);
                        CL.add(bestOut);
                    }
                    if (bestIn != null) {
                        sol.add(bestIn);
                        CL.remove(bestIn);
                    }
                    ObjFunction.evaluate(sol);
                    improved = true;
                }
            }
        } while (improved && (now() - tStart) <= timeLimitNanos);

        return sol;
    }

    /* ----------------------- Lógica Reactive ----------------------- */

    private int sampleAlphaIndex() {
        double u = rng.nextDouble();
        double acc = 0.0;
        for (int i = 0; i < probs.length; i++) {
            acc += probs[i];
            if (u <= acc) return i;
        }
        return probs.length - 1;
    }
    private void recomputeReactiveProbs() {
        double[] score = new double[alphas.length];
        double sum = 0.0;
        for (int i = 0; i < alphas.length; i++) {
            double g = (countAlpha[i] > 0 ? avgGain[i] : 0.0);
            double s = (g > 0 ? (bestSoFar / g) : 1e-9);
            score[i] = s; sum += s;
        }
        for (int i = 0; i < alphas.length; i++) probs[i] = score[i] / sum;
    }

    @Override
    public Solution<Integer> solve() {
        long t0 = System.nanoTime();
        bestSol = createEmptySol();
        bestCost = Double.POSITIVE_INFINITY;
        iterationsRun = 0;        // NEW
        bestIter = -1;            // NEW
        bestTimeSec = 0.0;        // NEW

        if (mode != ConstructionMode.REACTIVE) {
            for (int it = 0; it < iterations; it++) {
                if ((System.nanoTime() - t0) > timeLimitNanos) break;
                constructiveHeuristic();
                localSearch();
                iterationsRun++; // NEW
                if (bestSol.cost > sol.cost) {
                    bestSol = new Solution<>(sol);
                    bestCost = bestSol.cost;
                    // NEW: marca iteração e tempo do melhor
                    bestIter = iterationsRun;
                    bestTimeSec = (System.nanoTime() - t0) / 1e9;
                    if (verbose) System.out.println("(Iter " + it + ") Best = " + bestSol);
                }
            }
            return bestSol;
        }

        int it = 0, blockCount = 0;
        while ((System.nanoTime() - t0) <= timeLimitNanos && it < iterations) {
            int idx = sampleAlphaIndex();
            this.alpha = alphas[idx];

            constructiveHeuristic();
            localSearch();
            iterationsRun++; // NEW

            if (bestSol.cost > sol.cost) {
                bestSol = new Solution<>(sol);
                bestCost = bestSol.cost;
                double bestF = -bestCost;
                if (bestF > bestSoFar) bestSoFar = bestF;
                // NEW:
                bestIter = iterationsRun;
                bestTimeSec = (System.nanoTime() - t0) / 1e9;
                if (verbose) System.out.println("(Iter " + it + ") [alpha=" + alpha + "] Best = " + bestSol);
            }

            double thisF = -sol.cost;
            countAlpha[idx] += 1;
            double old = avgGain[idx];
            avgGain[idx] = old + (thisF - old) / countAlpha[idx];

            blockCount++;
            if (blockCount >= reactiveBlock) {
                recomputeReactiveProbs();
                blockCount = 0;
            }
            it++;
        }
        return bestSol;
    }
}
