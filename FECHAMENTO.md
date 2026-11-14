# Fechamento mensal

`GET /reports/closing?year=2026&month=10` — Basic Auth, ADMIN. Ano 1900–9999,
mês 1–12; inválidos retornam 400. Mês vazio: 200, linhas vazias, totais zero,
percentuais nulos, inclusive faturamento. Contrato:

```json
{"year":2026,"month":10,"timeZone":"America/Sao_Paulo","movementDays":1,
 "days":[{"date":"2026-10-01","waterProfit":0,"gasProfit":15,"purchases":0,
 "expenses":0,"dailyBalance":15,"accumulatedBalance":15,"withdrawals":0}],
 "totals":{"waterProfit":0,"gasProfit":15,"purchases":0,"expenses":0,
 "dailyBalance":15,"accumulatedBalance":15,"withdrawals":0},
 "summary":{"revenue":{"value":125,"percentage":100},
 "purchases":{"value":0,"percentage":0},"expenses":{"value":0,"percentage":0},
 "profitMargin":{"value":15,"percentage":12}},"warnings":[]}
```

Valores e percentuais são números; percentual já vem na escala 0–100.
Frações racionais exatas evitam arredondamento por item/parcela/dia. Somente a
representação JSON de frações periódicas usa DECIMAL128, com 34 algarismos.
O desktop apenas formata duas casas, sem recalcular valores ou percentuais.
14.209,50 / 6.204,10 / 3.408,00 / 4.597,40 resulta em 43,66%, 23,98% e **32,35%**.
Não ajustar percentual para forçar a soma exibida a 100%.

## Schema e compatibilidade

V2 acrescenta `withdrawal_kind` (RETIRADA por padrão), `payment_method` (DINHEIRO
por padrão) em `tb_cash_movement`; `unit_cost` e `cost_estimated` no item.
Preenche custo antigo com `prod_last_cost_price` atual, marcado como estimado.
Custo desconhecido fica NULL: bruto entra, lucro desconhecido fica fora com aviso.
V3 incorpora as colunas/tabela de operações já presentes na main (fiado manual,
garrafões, promoção e meio do acerto). As branches de instalação/Flyway eram
anteriores a essas operações. Foram integradas localmente. **V1 intocada**;
nenhum baseline automático, nenhuma alteração de preços, IDs, saldos ou datas.

`POST /cash-movements` preserva `type: WITHDRAWAL`, valor, motivo obrigatório e
data. Acrescenta `withdrawalKind: RETIRADA|COMPRA|DESPESA` e
`paymentMethod: DINHEIRO|PIX`, opcionais com os padrões acima. Responses devolvem
os dois campos. Entrada/ajuste continuam só em dinheiro. Todas as saídas afetam
o meio escolhido no Caixa do Dia; apenas compra/despesa reduzem lucro no Fechamento.

Novos itens congelam `Product.costPrice`; gás usa `gasCostPrice` efetivo, quando
informado. Mudança posterior no catálogo não muda lucro passado. Correções
explícitas de produto/custo continuam pelo fluxo existente.

## Análise e decisões conservadoras

- Água/gás: `Product.type` WATER/GAS. Preço cobrado: `OrderItem.unitPrice`, depois
  de preço manual, `tb_client_price`, revenda e desconto. Custo: `Product.costPrice`.
  Fornecedor não possui custo próprio. Acerto não é a fonte do custo do fechamento.
- PIX/DINHEIRO: valor e lucro proporcionais ao total do pedido, na data da parcela,
  inclusive pagamento antes da entrega. Cancelados ficam fora. O cancelamento
  existente remove pagamentos; Payment não tem status/data próprios de estorno.
- Datas são timestamps sem fuso, interpretados como São Paulo, conforme
  `Application.init` já configura. Intervalo mensal inclui início e exclui fim.
  Não é possível recuperar outro fuso de origem de dados antigos sem offset.
- Créditos/adiantamentos sem venda identificada não são faturamento de venda.
  SALDO não é recebimento novo e não tem rastreio até o crédito original: fica
  fora com aviso. Reconhecer na data original exigiria uma regra nova de rastreio.
- Fiados antigos sem itens/custos ficam fora com aviso; fiados de pedidos reais
  entram na data do pagamento, normalmente.
- Gás recebido pelo entregador não cria Payment. Sem data do pagamento do cliente,
  usa a data do acerto SUPPLIER_OWE recebido para reconhecer bruto e margem dos
  itens; aviso explícito. YOU_OWE é repasse, não despesa/lucro. Acerto editável
  não redefine custo/margem do item. Pagamento já existente nunca é duplicado.
- Promoção usa preços cobrados: água inclusa a zero tem perda igual ao custo;
  gás carrega o valor do conjunto. Somados, dão o lucro real, sem receita fictícia
  pelo valor de referência da água.
- Empréstimos e dívidas de vasilhames não têm receita. FULL/EXPIRED têm vendas no
  código, mas ficam fora do fechamento com aviso até definir o custo do vasilhame.
- Entradas/ajustes não são vendas. Retirada aparece apenas como informação.

## Verificação

`ClosingMigrationPostgresTest`: opt-in `-Dclosing.postgres=true`, somente container
`vitalis-fechamento-pg16`, Postgres 16.3, 127.0.0.1:55439. Cada execução cria banco
novo. Aplica V1, insere sete registros antigos, gera `target/closing-before-v2.dump`,
aplica V2/V3 e compara todas as colunas originais em todas as tabelas, além dos
custos/defaults. Resultado: `target/closing-migration-verification.txt`.
Não usa containers/banco operacionais. Hibernate validate é executado também no
Postgres descartável. Suíte H2 usa Flyway desligado.
Referência anterior: 234 testes, 5 falhas, 24 erros, 1 ignorado; relatórios em
`target/baseline-reports`, para comparar as identidades das falhas após mudanças.

Validação realizada: 15 testes novos de fechamento passaram; migration V1 → V2 →
V3 com sete registros antigos e comparação das 15 tabelas passou; Hibernate
validate no Postgres 16.3 passou. Suíte posterior: 250 testes, 5 falhas, 24 erros,
2 ignorados (testes Postgres são opt-in). As identidades dos 29 testes com falha
são exatamente iguais às da referência; nenhuma regressão nova.
