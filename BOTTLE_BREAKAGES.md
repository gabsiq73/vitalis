# Registro de garrafões quebrados

Em Estoque, a quebra retira a quantidade do total de garrafões válidos ou
vencidos escolhido pelo operador. O histórico mantém categoria, quantidade,
data, observação opcional, água associada quando cheio e usuário que registrou.

## Contratos

Todas as rotas usam Basic Auth, como o controle de estoque existente.

- `POST /bottle-inventory/breakages`: retorna 201 e BottleBreakageResponseDTO.
- `GET /bottle-inventory/breakages`: Page do Spring, padrão 15 registros,
  ordenados por occurredAt/createDate decrescente.
- `GET /bottle-inventory` mantém todos os campos anteriores e acrescenta
  `brokenValidQuantity` e `brokenExpiredQuantity`, totais de unidades quebradas
  desde o início do histórico. Essas unidades já foram retiradas do estoque
  físico; não devem ser subtraídas novamente.

Corpo de um lançamento:

```json
{"validity":"VALID","quantity":2,"notes":"Quebra no depósito"}
```

`validity`: VALID ou EXPIRED. `quantity`: inteiro positivo. `notes`: opcional,
até 255 caracteres. `occurredAt` pode ser informado no passado ou presente;
omitido, recebe a data/hora atual de São Paulo.

Para garrafões cheios, acrescente `waterProductId` da água correspondente.
A mesma quantidade é retirada desse produto WATER e da categoria de garrafão.
Um lançamento reúne somente uma categoria e uma água; quebras de tipos ou
marcas diferentes são lançamentos separados.

Response: id, validity, quantity, occurredAt, notes, waterProductId,
waterProductName e createdBy.

## Consistência

- Exige contagem física inicial de garrafões configurada.
- Bloqueia a linha de inventário durante a operação, como os outros movimentos.
- Garrafão válido vazio só pode sair da quantidade válida que não está alocada
  às águas cheias. Para cheio, é obrigatório selecionar a água a baixar.
- Não permite categoria/água inválida, quantidade insuficiente nem saldo negativo.
- Baixa física e histórico são gravados na mesma transação. Se uma validação
  falha, nenhuma baixa ou registro parcial fica salvo.
- Quebra não cria pedido, pagamento, despesa ou venda no fechamento.
- Recontar os totais físicos não apaga nem zera os contadores do histórico.
- Produtos citados em quebras são desativados ao excluir e mantêm seu tipo,
  preservando a referência ao produto do histórico.

## Banco e validação

A configuração atual usa Flyway com Hibernate validate. A V6 cria somente
`bottle_breakage`, FK opcional para produto e índice de data; não modifica
as migrations V1–V5 nem regrava os registros/totais existentes. Não habilitar
ddl-auto update nem baseline automático para instalar esse recurso.

`BottleBreakageTest` cobre baixas válidas/vencidas, vazios/cheios, estoque zero,
estoque insuficiente, validações, auditoria, histórico, recontagem e preservação
de produtos. Validar também BottleProductsAndGasSalePriceTest e
ClientCreditAndDeferredPromotionTest.

Teste Electron: `tests/bottleBreakage.electron.mjs` no desktop, com API real
descartável em `BOTTLE_BREAKAGE_TEST_API=http://127.0.0.1:18090`.

Arquivos novos: BottleValidity, BottleBreakage, BottleBreakageRepository,
BottleBreakageMapper, BottleBreakageService, BottleBreakageRequestDTO,
BottleBreakageResponseDTO, V6__bottle_breakage_history.sql e BottleBreakageTest.
Alterados: BottleInventoryController, BottleInventoryService,
BottleInventoryResponseDTO e ProductService. No desktop: StockPage,
RegisterBottleBreakageModal, tipos da API, README e teste Electron.
