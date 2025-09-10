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

    /* ---------- Utilidades de parsing ---------- */

    /** Lê a próxima linha não vazia; lança exceção clara se EOF for atingido. */
    private static String readNonEmpty(BufferedReader br, String ctx) throws IOException {
        String line;
        while (true) {
            line = br.readLine();
            if (line == null)
                throw new IOException("Fim de arquivo inesperado ao ler " + ctx);
            if (!line.trim().isEmpty())
                return line;
            // pula linhas em branco
        }
    }

    public SCQBF(String filename) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            // n
            String nLine = readNonEmpty(br, "n");
            n = Integer.parseInt(nLine.trim());

            // tamanhos dos conjuntos
            String sizesLine = readNonEmpty(br, "tamanhos dos conjuntos");
            String[] parts = sizesLine.trim().split("\\s+");
            if (parts.length != n) {
                throw new IOException("Linha de tamanhos dos conjuntos != n (" + parts.length + " vs " + n + ")");
            }
            int[] sz = new int[n];
            for (int i = 0; i < n; i++) {
                if (parts[i].isEmpty())
                    throw new IOException("Token vazio em tamanhos na posição " + i);
                sz[i] = Integer.parseInt(parts[i]);
                if (sz[i] < 0) {
                    throw new IOException("|S_" + i + "| negativo: " + sz[i]);
                }
            }

            // S_i
            sets = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                if (sz[i] == 0) {
                    sets.add(new int[0]);
                } else {
                    String line = readNonEmpty(br, "S_" + i);
                    String[] toks = line.trim().split("\\s+");
                    if (toks.length != sz[i]) {
                        throw new IOException("S_" + i + ": esperado " + sz[i] + " elementos, mas veio " + toks.length);
                    }
                    int[] list = new int[toks.length];
                    for (int t = 0; t < toks.length; t++) {
                        if (toks[t].isEmpty())
                            throw new IOException("Token vazio em S_" + i + " idx " + t);
                        int v = Integer.parseInt(toks[t]); // 1-based no arquivo
                        int v0 = v - 1;                    // 0-based interno
                        if (v0 < 0 || v0 >= n) {
                            throw new IOException("Elemento fora do domínio em S_" + i + ": " + v + " (válido: 1.."+ n +")");
                        }
                        list[t] = v0;
                    }
                    sets.add(list);
                }
            }

            // Matriz A (triangular superior no arquivo)
            double[][] T = new double[n][n];
            for (int i = 0; i < n; i++) {
                String line = readNonEmpty(br, "A[" + i + ", i..n-1]");
                String[] toks = line.trim().split("\\s+");
                int expected = (n - i);
                if (toks.length != expected) {
                    throw new IOException("A[" + i + "]: esperado " + expected + " valores, veio " + toks.length);
                }
                for (int j = i; j < n; j++) {
                    String tok = toks[j - i];
                    if (tok.isEmpty())
                        throw new IOException("Token vazio em A[" + i + "," + j + "]");
                    T[i][j] = Double.parseDouble(tok);
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
        // Reconstrói incrementalmente (rápido e simples)
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
        // w[i] = Σ_j x[j] * symA(i,j)
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
