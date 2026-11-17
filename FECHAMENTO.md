# Fechamento mensal

`GET /reports/closing?year=2026&month=10` — Basic Auth, ADMIN. Ano 1900–9999,
mês 1–12; inválidos retornam 400. Mês vazio: 200, linhas vazias, totais zero,
percentuais nulos, inclusive faturamento.

## Regras

- Vendas são pedidos DELIVERED, na data de `deliveryDate`, incluindo fiados.
  Pedidos pendentes, em transporte e cancelados ficam fora. Recebimentos,
  pagamentos parciais, SALDO e acertos de gás não definem nem repetem a venda.
- **Vendas água**: quantidade × preço efetivamente cobrado, sem descontar custo.
  O nome JSON legado `waterProfit` foi preservado para compatibilidade.
- **Vendas gás** (`gasProfit`): quantidade × (venda − custo congelado do item),
  independentemente de quem recebeu ou da liquidação do acerto.
- **Compras**: pagamentos de compras de água/boletos lançados no Caixa do Dia
  como WITHDRAWAL/COMPRA. Não são o custo calculado dos produtos vendidos nem
  entradas de estoque. A classificação existente é preservada, sem reinterpretar
  motivos de lançamentos antigos automaticamente.
- **Despesas**: WITHDRAWAL/DESPESA do Caixa do Dia.
- **Retirada**: WITHDRAWAL/RETIRADA, incluindo saídas legadas sem classificação.
  É a parte da empresa, apenas demonstrativa; não afeta o resultado do fechamento.
- **Saldo do dia** = (Vendas água − Compras) + Vendas gás − Despesas.
- **Saldo do mês**: soma dos saldos diários em ordem cronológica desde o início
  do mês selecionado. Reinicia a cada mês. Ex.: dia 1 = 200, dia 2 = 250,
  acumulado no dia 2 = 450. O total mensal mostra 450, sem somar os acumulados.
- Compras, despesas e retiradas são consideradas na data do lançamento,
  tanto em DINHEIRO quanto PIX, cada saída uma única vez.

Exemplo: 10 águas a R$ 10, gás a R$ 120 com custo R$ 110, boleto de R$ 40,
despesa de R$ 15 e retirada de R$ 20 resultam em água 100, gás 10 e saldo 55.
A retirada de 20 permanece visível, sem reduzir o saldo.

`summary.revenue` continua sendo o faturamento bruto das vendas consideradas,
incluindo o preço integral do gás. `summary.profitMargin.value` é o saldo mensal
pela fórmula acima; os percentuais são sobre esse faturamento bruto.

## Contrato

```json
{"year":2026,"month":10,"timeZone":"America/Sao_Paulo","movementDays":1,
 "days":[{"date":"2026-10-01","waterProfit":100,"gasProfit":10,"purchases":40,
 "expenses":15,"dailyBalance":55,"accumulatedBalance":55,"withdrawals":20}],
 "totals":{"waterProfit":100,"gasProfit":10,"purchases":40,"expenses":15,
 "dailyBalance":55,"accumulatedBalance":55,"withdrawals":20},
 "summary":{"revenue":{"value":220,"percentage":100},
 "purchases":{"value":40,"percentage":18.18181818181818},
 "expenses":{"value":15,"percentage":6.818181818181818},
 "profitMargin":{"value":55,"percentage":25}},"warnings":[]}
```

Valores e percentuais são números, percentuais na escala 0–100. A API mantém
aritmética racional exata; apenas frações periódicas usam DECIMAL128 no JSON.
O desktop formata duas casas sem recalcular.

## Casos especiais e compatibilidade

- Nenhuma migration ou regravação de dados é necessária para esta revisão.
  Os campos e endpoints existentes permanecem; muda a regra da consulta.
- Água com custo desconhecido entra integralmente. Gás com custo desconhecido
  entra no faturamento, mas sua margem fica fora com aviso. Custos antigos
  estimados continuam identificados; alterações no catálogo não mudam o custo
  congelado. Correções explícitas continuam pelo fluxo de correção existente.
- Troca de água em garrafão vencido conta o preço cobrado como venda de água.
  Venda de vasilhame vencido e garrafão completo continuam fora com aviso,
  até definir o tratamento do vasilhame.
- Promoção respeita o preço cobrado: água inclusa a zero conta zero em água;
  gás carrega o preço do conjunto menos o custo do gás. Custo da água não é
  descontado por item, pois as compras de água são lançadas separadamente.
- Fiados antigos manuais não são vendas novas e não entram. Créditos,
  adiantamentos, entradas e ajustes de caixa também não são vendas.
- Datas são timestamps locais de São Paulo. O mês inclui o início e exclui
  o início do mês seguinte.
- O fechamento representa o resultado pela regra comercial acima; é diferente
  do dinheiro/PIX efetivamente disponível, que continua no Caixa do Dia.

## Verificação

`MonthlyClosingTest`: banco H2 descartável, sem dados operacionais. Cobre
água bruta, margem do gás, fiado, pagamentos posteriores/parciais/excedentes,
SALDO, pedido misto, cancelamento, promoções, garrafão vencido, custo desconhecido,
saídas em dinheiro/PIX, saldo negativo, acumulado e virada do mês.
Validação desta revisão: 20 testes passaram, sem falhas. O teste Electron
do desktop passou com a API real em H2 descartável; nenhum banco operacional
foi utilizado.

`ClosingMigrationPostgresTest` permanece opt-in para as migrations já existentes:
`-Dclosing.postgres=true`, container descartável `vitalis-fechamento-pg16`.
Esta alteração não modifica migrations e não executa baseline no banco operacional.
