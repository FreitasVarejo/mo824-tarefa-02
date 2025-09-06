package problems.scqbf;

import java.io.IOException;

/**
 * Mantém a mesma interface da QBF_Inverse: o SCQBF já retorna custos = –f,
 * então esta classe existe para manter o padrão de uso no solver.
 */
public class SCQBF_Inverse extends SCQBF {
    public SCQBF_Inverse(String filename) throws IOException {
        super(filename);
    }
    // Os métodos de SCQBF já retornam custo = –f e –Δf, nada a sobrescrever.
}
