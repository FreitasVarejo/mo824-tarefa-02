# MO824 — Atividade 2: GRASP para SCQBF

Implementação de GRASP para **SCQBF** (Set Covering Quadratic Binary Function), isto é, maximização de \(f(x)=x^\top A x\) com **restrição de cobertura** (cada elemento deve ser coberto por ao menos um conjunto selecionado). O framework é de **minimização**, então otimizamos **–f(x)** e adicionamos penalidade para cobertura quando necessário.

## O que está implementado
- **Problema:** SCQBF (leitura no formato da A1: `n`, linha com |Sᵢ|, `n` linhas de Sᵢ, matriz A triangular superior).
- **GRASP (3 construções):**
  1. **Padrão** (RCL por α)
  2. **Sampled Greedy** (amostra `p` candidatos por passo)
  3. **Reactive GRASP** (ajuste de α por blocos)
- **Busca local (2 modos):** *first-improving* e *best-improving*  
  Vizinhanças: **add**, **drop** (sem quebrar cobertura), **swap**.
- **Runner:** roda as 5 configurações pedidas (duas α, first/best, sampled, reactive) por **tempo fixo** (ex.: 30 min/instância) e gera **CSV**.

## Estrutura
```

src/
metaheuristics/grasp/AbstractGRASP.java      # base do framework
problems/scqbf/SCQBF.java                    # avaliador do SCQBF (–f e deltas)
problems/scqbf/SCQBF\_Inverse.java            # compatibilidade com o framework
problems/scqbf/solvers/GRASP\_SCQBF.java      # solver GRASP (3 construções + 2 buscas)
RunnerSCQBF.java                              # executa as 5 configs e gera CSV
instances/
scqbf/                                       # coloque aqui as 15 instâncias da A1 (formato A1)

````

## Pré-requisitos
- **JDK 21+** (`javac -version`)
- Linux/macOS; no Fedora: `sudo dnf install -y java-21-openjdk-devel git gh`

## Compilar
```bash
mkdir -p bin
javac -d bin $(find src -name "*.java")
````

## Rodar

**Opção 1 — diretório com as 15 instâncias (formato A1):**

```bash
java -cp bin RunnerSCQBF instances/scqbf resultados_scqbf.csv 30 42
#                                      ^dir instâncias   ^CSV  ^min ^seed
```

**Opção 2 — arquivo-lista (um caminho por linha):**

```bash
java -cp bin RunnerSCQBF lists/scqbf_list.txt resultados_scqbf.csv 30 42
```

## Saída (CSV)

Colunas:
`instance, config, alpha, mode, ls, best_f, time_s, time_to_best_s, iters, best_iter, seed`

* **best\_f** = melhor valor de $f$ (positivo, pois imprimimos –cost)
* **time\_to\_best\_s** = segundos até achar o melhor
* **iters**/**best\_iter** = iterações totais / iteração do melhor
* **seed** = semente para reprodutibilidade

## Notas

* As **15 instâncias do lab anterior** devem ser usadas aqui, conforme o enunciado.
* Penalidade de cobertura garante que a construção/busca não aceite soluções inviáveis.

EOF

# adicionar e commitar o README

git add README.md
git commit -m "README: visão geral, como compilar/rodar e saída"

````

### (Opcional) Criar `.gitignore` para Java
```bash
cat > .gitignore << 'EOF'
# Build
bin/
out/
target/
*.class
*.jar
# IDEs
*.iml
.idea/
.project
.classpath
.settings/
# SO
.DS_Store
Thumbs.db
# Resultados
*.csv
*.log
EOF

git add .gitignore
git commit -m "Add .gitignore (Java/build/IDE)"
````

### Enviar mudanças

```bash
git push origin HEAD
```


