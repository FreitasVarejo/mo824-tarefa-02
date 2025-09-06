package problems.scqbf;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import problems.Evaluator;
import solutions.Solution;

/**
 * SCQBF = Set Covering Quadratic Binary Function:
 * Maximiza f(x) = x' A x, sujeito à cobertura: cada elemento k (0..n-1)
 * deve aparecer em pelo menos um conjunto selecionado S_i (x_i = 1).
 *
 * Este Evaluator segue o framework (minimização) retornando custo = -f(x),
 * e fornece deltas de inserção/remoção/troca em O(1) a partir de estado
 * incremental reconstruído a cada avaliação a partir de sol (simples e robusto).
 *
 * Formato da instância (mesmo da Atividade 1):
 * n
 * |S_0| |S_1| ... |S_{n-1}|
 * linha com elementos de S_0 (1-based, pode ser vazia se |S_0|=0)
 * ...
 * linha com elementos de S_{n-1}
 * A[0,0..n-1]
 * A[1,1..n-1]
 * ...
 * A[n-1,n-1]
 */
public class SCQBF implements Evaluator<Integer> {

    public final int n;                  // número de variáveis / conjuntos
    public final List<int[]> sets;       // S_i (0-based)
    public final double[][] A;           // matriz triangular superior conforme framework

    // Estado incremental associado a uma Solution corrente (reconstruído quando necessário)
    boolean[] x;     // seleção atual
    int[] cover;     // cobertura por elemento k (0..n-1)
    double[] w;      // w[i] = sum_j x[j]*(A[i][j] + A[j][i])
    double f;        // valor atual f(x) = x' A x

    public SCQBF(String filename) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            // n
            n = Integer.parseInt(br.readLine().trim());
            // tamanhos dos conjuntos
            String[] parts = br.readLine().trim().split("\\s+");
            if (parts.length != n) {
                throw new IOException("Linha de tamanhos dos conjuntos não tem n entradas.");
            }
            int[] sz = new int[n];
            for (int i = 0; i < n; i++) sz[i] = Integer.parseInt(parts[i]);

            // S_i
            sets = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                if (sz[i] == 0) {
                    sets.add(new int[0]);
                } else {
                    String line = br.readLine();
                    if (line == null) throw new IOException("Faltou linha de conjunto para i=" + i);
                    String[] toks = line.trim().split("\\s+");
                    int[] list = new int[toks.length];
                    for (int t = 0; t < toks.length; t++) {
                        // instâncias A1 são 1-based para elementos: converter para 0-based
                        list[t] = Integer.parseInt(toks[t]) - 1;
                    }
                    sets.add(list);
                }
            }

            // Matriz A (triangular superior no arquivo)
            double[][] T = new double[n][n];
            for (int i = 0; i < n; i++) {
                String line = br.readLine();
                if (line == null) throw new IOException("Faltou linha da matriz A para i=" + i);
                String[] toks = line.trim().split("\\s+");
                if (toks.length != (n - i)) {
                    throw new IOException("Linha A[" + i + ", i..n-1] com tamanho incorreto.");
                }
                for (int j = i; j < n; j++) {
                    T[i][j] = Double.parseDouble(toks[j - i]);
                }
            }

            // Armazenar em A no mesmo espírito do QBF do professor:
            // A[i][j] preenchida apenas para j>=i, e A[j][i] = 0 para j>i.
            A = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = i; j < n; j++) {
                    A[i][j] = T[i][j];
                    if (j > i) A[j][i] = 0.0;
                }
            }

            // Estado
            x = new boolean[n];
            cover = new int[n];
            w = new double[n];
            f = 0.0;
        }
    }

    @Override
    public Integer getDomainSize() {
        return n;
    }

    /* ---------- Utilidades internas ---------- */

    private double symA(int i, int j) {
        // coerente com QBF do prof: usa A[i][j] + A[j][i]
        return A[i][j] + A[j][i];
    }

    private void resetState() {
        Arrays.fill(x, false);
        Arrays.fill(cover, 0);
        Arrays.fill(w, 0.0);
        f = 0.0;
    }

    private void rebuildFromSolution(Solution<Integer> sol) {
        resetState();
        // Reconstroi incrementalmente (rápido e simples)
        for (int e : sol) {
            applyAdd(e, true);
        }
    }

    private boolean canDrop(int i) {
        for (int k : sets.get(i)) {
            if (cover[k] <= 1) return false;
        }
        return true;
    }

    private void applyAdd(int i, boolean touchF) {
        if (x[i]) return;
        double deltaF = A[i][i] + w[i];
        if (touchF) f += deltaF;
        // atualizar w[j] para j já selecionados
        for (int j = 0; j < n; j++) if (x[j]) {
            double s = symA(i, j);
            w[j] += s;
        }
        // w[i] deve refletir Σ_j x[j] * symA(i,j)
        // O laço acima já somou nas posições w[j]; w[i] ainda não tem os simétricos
        // mas como não usamos w[i] imediatamente após add aqui, não há problema prático.
        // (Se quiser precisão imediata: calcule w[i] = Σ_j x[j]*symA(i,j) em um laço.)
        double wi = 0.0;
        for (int j = 0; j < n; j++) if (x[j]) wi += symA(i, j);
        w[i] = wi;
        x[i] = true;
        for (int k : sets.get(i)) cover[k] += 1;
    }

    private void applyDrop(int i, boolean touchF) {
        if (!x[i]) return;
        double deltaF = -(A[i][i] + w[i]);
        if (touchF) f += deltaF;
        for (int j = 0; j < n; j++) if (x[j] && j != i) {
            double s = symA(i, j);
            w[j] -= s;
        }
        w[i] = 0.0;
        x[i] = false;
        for (int k : sets.get(i)) cover[k] -= 1;
    }

    /* ---------- Métodos do Evaluator ---------- */

    @Override
    public Double evaluate(Solution<Integer> sol) {
        rebuildFromSolution(sol);
        // Minimiza –f
        return sol.cost = -f;
    }

    @Override
    public Double evaluateInsertionCost(Integer elem, Solution<Integer> sol) {
        rebuildFromSolution(sol);
        int i = elem;
        if (x[i]) return 0.0; // já dentro
        double deltaF = A[i][i] + w[i];
        return -deltaF; // custo = –Δf
    }

    @Override
    public Double evaluateRemovalCost(Integer elem, Solution<Integer> sol) {
        rebuildFromSolution(sol);
        int i = elem;
        if (!x[i]) return 0.0; // já fora
        if (!canDrop(i)) return Double.POSITIVE_INFINITY; // quebra cobertura
        double deltaF = -(A[i][i] + w[i]);
        return -deltaF;
    }

    @Override
    public Double evaluateExchangeCost(Integer elemIn, Integer elemOut, Solution<Integer> sol) {
        rebuildFromSolution(sol);
        int in = elemIn, out = elemOut;
        if (in == out) return 0.0;
        if (!x[out] && !x[in]) return evaluateInsertionCost(in, sol);
        if (x[out] && x[in])  return evaluateRemovalCost(out, sol);
        // só pode dropar out se não quebrar cobertura
        if (x[out] && !canDrop(out)) return Double.POSITIVE_INFINITY;

        // Δf ≈ add(in) + drop(out) − symA(in,out) (para ajustar duplo-contagem)
        double add = A[in][in] + w[in];
        double drop = -(A[out][out] + w[out]);
        double corr = symA(in, out);
        double deltaF = add + drop - corr;
        return -deltaF;
    }
}
