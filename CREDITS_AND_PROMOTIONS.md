# Saldo direto e água da promoção para entrega posterior

Todas as rotas exigem a autenticação Basic existente. Não há mudança de schema,
nova migration nem regravação de dados antigos.

## Saldo em reais

`POST /payments/credits/{clientId}`, corpo
`{"amount":100,"paymentMethod":"PIX"}`, retorna 204.
Valor positivo com até duas casas decimais e máximo 99.999.999,99.
Métodos aceitos: PIX e DINHEIRO; SALDO não pode criar crédito novo.
Cliente precisa estar ativo e ser cadastrado (não AVULSO).

Reutiliza o pagamento em lote: quita fiados/pedidos abertos por FIFO e deixa
o restante como crédito. Pagamentos e entradas de crédito preservam a origem
do dinheiro no Caixa do Dia sem duplicar recebimento. Ex.: dívida 30 e
depósito 100 deixam crédito 70. O uso posterior continua pelo método SALDO;
cancelamento do pedido devolve o saldo utilizado sem nova entrada no caixa.

## Água da promoção

`POST /orders` aceita `promotion.waterAsCredit: true`. O padrão omitido/false
preserva a promoção atual, com gás e água juntos.

Ao deixar para depois, o retorno contém dois pedidos: o gás com preço do
conjunto e a água a zero, PAID e PENDING. Ambos usam o grupo de cancelamento
existente. Os campos calculados do response são:

- `waterCredit: true`: pedido composto pela água já paga da promoção.
- `deferredWaterPromotion: true`: gás de uma promoção com água para depois.

Esses campos são calculados a partir dos itens e do grupo já persistidos.
Não reutilizam fidelidade nem convertem a água em saldo monetário.
Há uma água por gás. A água pode estar sem estoque na criação; somente o
resgate efetivo exige e baixa estoque. Nenhum pagamento novo é permitido no
pedido de água, porque ela já está paga no conjunto.

`GET /orders/client/{id}/water-credits` retorna todos os créditos pendentes
do cliente, em ordem de criação:

```json
[{"orderId":"uuid","productId":"uuid","productName":"Água","quantity":3,
  "createDate":"2026-11-15T12:00:00","available":true}]
```

`available` exige a entrega concluída do gás original.
`POST /orders/{id}/redeem-water`, corpo `{"quantity":1}`, entrega a quantidade
solicitada e retorna OrderResponseDTO. No resgate parcial, mantém o restante no
pedido original e registra a entrega separadamente, a zero, no mesmo grupo.
No integral, entrega o próprio pedido pendente. Bloqueios pessimistas no gás
original e no crédito serializam resgates, entregas e cancelamento da promoção.
Falha de estoque/quantidade é transacional e não consome crédito.

Cancelar um resgate entregue devolve a água ao estoque e ao crédito, sem
cancelar o gás. Cancelar o gás revoga todo o grupo e recompõe resgates já
entregues. Apagar definitivamente o gás ou seu acerto exclui a promoção inteira,
incluindo créditos e resgates. Apagar só um pedido de água é bloqueado.

Correções de preço de venda/custo do gás continuam permitidas. Produto e
quantidade da promoção com crédito são mantidos; para alterá-los, cancele e
registre a promoção novamente. Pedidos de água em crédito não podem editar
preço/quantidade pelo formulário genérico. As promoções anteriores permanecem
editáveis pelo fluxo anterior.

O fechamento reconhece o preço do conjunto no gás, pela regra vigente, e
resgates a zero não geram faturamento nem recebimento adicionais. Estoque
e contagem de águas entregues mudam somente na entrega real da água.

## Validação e arquivos

`ClientCreditAndDeferredPromotionTest` cobre depósitos, fiados, SALDO, validações,
promoção imediata e diferida, falta de estoque, resgate parcial/integral,
cancelamento, exclusão pelo gás/acerto, correção e retirada com pagamento inicial.
Também validar GasPriceAndDeletionTest e MonthlyClosingTest.

Arquivos do backend: DTOs ClientCreditRequestDTO, RedeemWaterCreditDTO,
GasWaterPromotionRequestDTO, WaterCreditResponseDTO e OrderResponseDTO; model
Order; controllers PaymentController e OrderController; services PaymentService,
OrderService, OrderCreationService e CompletedOrderCorrectionService; teste
ClientCreditAndDeferredPromotionTest. O desktop mantém os tipos correspondentes,
AddClientCreditModal, RedeemWaterCreditModal, NewGasWaterPromotionModal,
EditCompletedOrderModal e as páginas ClientDetail, OrderDetail, Orders,
GasSettlements e Bottles.
