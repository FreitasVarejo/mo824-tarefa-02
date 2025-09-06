# MO824 — Atividade 2: GRASP para SCQBF

Implementação de GRASP para **SCQBF** (Set Covering Quadratic Binary Function): maximização de $f(x)=x^\top A x$ com **restrição de cobertura** (cada elemento deve ser coberto por ao menos um conjunto selecionado).
O framework é de **minimização**, então otimizamos **–$f(x)$**. **Não** usamos penalidade: a cobertura é tratada como **restrição dura nos movimentos** (remoções/trocas que a violem são proibidas).

## O que está implementado

* **Problema (SCQBF):** leitor no **formato da A1**
  `n`
  linha com $|S_i|$ para $i=0..n-1$
  `n` linhas com os elementos de cada $S_i$ (1-based no arquivo; convertidos para 0-based)
  matriz **A triangular superior** (linhas i…n-1)
* **GRASP — 3 construções:**

  1. **Padrão** (RCL por α, como no `AbstractGRASP`)
  2. **Sampled Greedy** (*p* candidatos amostrados por passo; escolhe o melhor da amostra)
  3. **Reactive GRASP** (conjunto $\Psi$ de α; probabilidades ajustadas por blocos)
* **Busca local — 2 modos:** *first-improving* e *best-improving*
  Vizinhanças: **add**, **drop** (*só* se não quebra cobertura), **swap** (opcional).
* **Runner:** executa as **5 configurações pedidas** (duas α, first/best, sampled, reactive) por **tempo fixo** (ex.: 30 min/instância) e gera **CSV**.

> **Observação**: partimos da solução vazia e adicionamos enquanto houver ganho. Em instâncias mal formadas (elementos não pertencem a nenhum $S_i$) a cobertura é impossível ⇒ problema inviável. Use as **15 instâncias viáveis do lab passado** como o professor pediu.

## Estrutura do repositório

```
src/
  metaheuristics/grasp/AbstractGRASP.java      # base do framework (minimiza; rng com seed configurável)
  problems/scqbf/SCQBF.java                    # avaliador do SCQBF (–f e deltas; cobertura como restrição dura)
  problems/scqbf/SCQBF_Inverse.java            # compatível com a convenção de minimização
  problems/scqbf/solvers/GRASP_SCQBF.java      # solver GRASP (3 construções + 2 buscas; controle por tempo)
  RunnerSCQBF.java                              # roda as 5 configs e gera CSV

instances/
  scqbf/                                       # as 15 instâncias do lab passado (formato A1)
```

## Pré-requisitos

* **JDK 21+** com `javac` (`javac -version` deve funcionar)
* Linux/macOS (no Fedora: `sudo dnf install -y java-21-openjdk-devel`)

## Compilar

```bash
mkdir -p bin
javac -d bin $(find src -name "*.java")
```

## Rodar (usar as 15 instâncias do lab passado)

Você já tem:

```
instances/scqbf/
  inst_25_{aleatorio,balanceado,concentrado}.dat
  inst_50_{...}.dat
  inst_100_{...}.dat
  inst_200_{...}.dat
  inst_400_{...}.dat
```

### Opção 1 — Passar o diretório (recomendado)

```bash
java -cp bin RunnerSCQBF instances/scqbf resultados_scqbf.csv 30 42
#                                  ^instâncias           ^CSV        ^min ^seed
```

### Opção 2 — Arquivo-lista (um caminho por linha)

> Caminhos **relativos** são interpretados **em relação à pasta do arquivo-lista**.

```bash
# gerar lista só com as 15 (ignora inst_10_aleatorio.dat)
mkdir -p lists
ls instances/scqbf \
  | grep -E 'inst_(25|50|100|200|400)_(aleatorio|balanceado|concentrado)\.dat$' \
  | sed 's|^|instances/scqbf/|' > lists/a2_15.txt

java -cp bin RunnerSCQBF lists/a2_15.txt resultados_scqbf.csv 30 42
```

## Saída (CSV)

Colunas:

* `instance` — nome do arquivo da instância
* `config` — uma das 5 configs (STD α1 FIRST, STD α2 FIRST, STD α1 BEST, SAMPLED p=64 FIRST, REACTIVE FIRST)
* `alpha`, `mode`, `ls` — parâmetros principais
* `best_f` — melhor **$f$** (positivo; imprimimos –cost)
* `time_s` — tempo total da execução (s)
* `time_to_best_s` — tempo até encontrar o melhor (s)
* `iters` — iterações realizadas
* `best_iter` — iteração em que o melhor foi encontrado
* `seed` — semente (reprodutibilidade)

## Observações importantes

* **Cobertura** é tratada como **restrição dura** nos movimentos: **add** sempre viável; **drop/swap** só se a cobertura se mantém.
* Para cumprir o enunciado, use **exatamente as 15 instâncias do lab passado** $\{25,50,100,200,400\}\times\{aleatorio,balanceado,concentrado\}$.
* **Semente** pode ser configurada no Runner (quarto argumento).
* Se precisar lidar com instâncias potencialmente inviáveis do gerador, adicione um *reparo de cobertura* (pós-construção) antes da busca local.

## Troubleshooting

* **`NoSuchFileException`** ao usar arquivo-lista: confira caminhos; lembre que relativos são resolvidos **pela pasta do arquivo-lista**.
* **`javac: command not found`**: instale o pacote *devel* do JDK (no Fedora: `java-21-openjdk-devel`).
* Para um *smoke test* rápido, troque `30` por `5` (min) e rode só uma configuração.
