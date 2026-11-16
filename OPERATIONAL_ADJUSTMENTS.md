# Correções de cadastro, fiados, compras de garrafões e fechamento

## Contratos

- `DELETE /products/{id}` (ADMIN) apaga um cadastro sem histórico, seu estoque e
  preços personalizados. Os totais físicos de vasilhames são preservados; água
  removida da alocação volta a ser vasilhame vazio. Com pedidos (inclusive água
  selecionada em completo/troca), empréstimos, quebras ou compras vinculadas,
  retorna 400 orientando desativar. Não desativa silenciosamente. ID ausente: 404.
  O desktop exige digitar exatamente `Excluir produto` na confirmação.
- `PUT /orders/manual-debts/{id}` recebe o mesmo `ManualDebtRequestDTO` do cadastro:
  `{clientId, amount, debtDate, notes?}`. `amount` corrige o **total original**, não
  apenas o saldo aberto. Mantém cliente e ID; altera valor positivo com até duas
  casas, data não futura e observação. Retorna `OrderResponseDTO` (200), sem novos
  campos. Cancelado/pedido comum/outro cliente: 400; ID ausente: 404; validação: 422.
  Recalcula situação e dívida. Excedente já pago vira crédito, preservando data,
  meio e total recebido no caixa; SALDO excedente é devolvido sem nova entrada.
  A transação e o bloqueio de pedido seguem a correção existente.
- `POST /bottle-inventory/purchases` recebe
  `{quantity, waterProductId?, occurredAt?, notes?}`. Quantidade inteira positiva
  até 2147483647, data não futura (padrão: agora), observação até 255 caracteres.
  Sem água: compra de vazios. Com água ativa de tipo WATER: compra de cheios.
  Exige contagem física inicial. Aumenta válidos e, se cheio, a mesma quantidade
  da água. Não modifica vencidos, vendas, caixa, contas ou quebras. Registro e
  dois estoques são transacionais sob o mesmo bloqueio pessimista dos vasilhames.
  Resposta 201: `{id, quantity, occurredAt, notes, waterProductId,
  waterProductName, createdBy}`. Água ausente: 404; inválida/inativa, estoque não
  configurado ou limite total excedido: 400; validação: 422.
- `GET /bottle-inventory/purchases`: `Page<BottlePurchaseResponseDTO>`, padrão 15,
  ordenado por `occurredAt DESC, createDate DESC`. Datas, observação e operador
  ficam no histórico. Os endpoints exigem autenticação como o estoque existente.
- `GET /reports/closing` mantém todos os campos. `withdrawals` continua a soma
  das retiradas do caixa por dia/mês, mas não reduz `dailyBalance`,
  `accumulatedBalance` ou `summary.profitMargin`. Fórmula:
  `(waterProfit - purchases) + gasProfit - expenses`. Caixa físico e PIX
  continuam refletindo as saídas reais.

## Atualização e validação

Atualize API e desktop juntos. A migration aditiva V7 cria apenas
`bottle_purchase` e seu índice, seguindo V6, sem reescrever dados existentes.
Foi autorizada explicitamente apesar da instrução antiga de schema em AGENTS.md;
a configuração atual usa Flyway e `ddl-auto: validate`.

Testes dirigidos: `OperationalAdjustmentsTest`, `MonthlyClosingTest` e
`OrderSearchAndManualDebtTest`. Electron: `tests/operationalAdjustments.electron.mjs`
no desktop, com API real H2 descartável em 18092. Banco operacional não é usado.
