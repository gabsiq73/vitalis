# Vitalis — Documentação Técnica Completa

## Sumário

1. [Visão Geral do Projeto](#1-visão-geral-do-projeto)
2. [Stack Tecnológica](#2-stack-tecnológica)
3. [Configuração e Infraestrutura](#3-configuração-e-infraestrutura)
4. [Arquitetura e Estrutura de Pastas](#4-arquitetura-e-estrutura-de-pastas)
5. [Entidades e Banco de Dados](#5-entidades-e-banco-de-dados)
6. [Enums](#6-enums)
7. [DTOs (Data Transfer Objects)](#7-dtos-data-transfer-objects)
8. [Mappers](#8-mappers)
9. [Repositórios](#9-repositórios)
10. [Serviços (Business Logic)](#10-serviços-business-logic)
11. [Controllers e Endpoints](#11-controllers-e-endpoints)
12. [Segurança](#12-segurança)
13. [Tratamento de Erros](#13-tratamento-de-erros)
14. [Fluxos de Negócio](#14-fluxos-de-negócio)

---

## 1. Visão Geral do Projeto

O **Vitalis** é um sistema de gestão para distribuidoras de **água e gás**. Ele cobre todo o ciclo operacional do negócio: cadastro de clientes, controle de estoque, criação e entrega de pedidos, registros de pagamento, controle de vasilhames emprestados e relatórios financeiros.

O sistema também inclui uma camada de gestão de liquidações com fornecedores de gás, permitindo rastrear débitos e créditos entre a distribuidora e seus fornecedores.

---

## 2. Stack Tecnológica

| Componente | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.4.0 |
| Banco de Dados | PostgreSQL 16.3 |
| ORM | Spring Data JPA / Hibernate |
| Mapeamento | MapStruct 1.5.5 |
| Redução de Boilerplate | Lombok 1.18.38 |
| Segurança | Spring Security (HTTP Basic + OAuth2 Client) |
| Documentação da API | SpringDoc OpenAPI 2.8.5 (Swagger UI) |
| Build | Maven 3 |
| Testes | JUnit 5 + H2 (in-memory) |

---

## 3. Configuração e Infraestrutura

### application.yaml

```
Banco:        PostgreSQL em localhost:5434
Usuário:      postgres / postgres
Database:     vitalis
DDL:          create-drop (recria o schema a cada inicialização)
Logging:      TRACE para com.vitalis.demo, WARN para root
```

### docker-compose.yml

O projeto inclui um arquivo Docker Compose que sobe o banco de dados PostgreSQL:

- **Serviço**: `vitalisdb` — PostgreSQL 16.3
- **Porta**: `5434 → 5432`
- **Volume**: `postgres_data` (persistência dos dados)
- **Rede**: `vitalis-network` (rede externa — deve ser criada manualmente antes do compose)

Para subir o banco:

```bash
docker network create vitalis-network
docker-compose up -d
```

---

## 4. Arquitetura e Estrutura de Pastas

O projeto segue a arquitetura em camadas padrão do Spring Boot:

```
src/main/java/com/vitalis/demo/
├── config/             # Configurações (auditoria, segurança, CORS)
├── controller/         # Endpoints REST
├── dto/
│   ├── request/        # DTOs de entrada (criação)
│   ├── response/       # DTOs de saída
│   └── update/         # DTOs de atualização parcial
├── infra/
│   ├── exception/      # Exceções customizadas
│   └── handler/        # GlobalExceptionHandler
├── mapper/             # Interfaces MapStruct
├── model/
│   └── enums/          # Enumerações de domínio
├── repository/         # Interfaces Spring Data JPA
├── security/           # Configuração de segurança
├── service/            # Regras de negócio
└── validator/          # Validadores customizados
```

---

## 5. Entidades e Banco de Dados

Todas as entidades herdam de **BaseEntity**, que fornece os campos de auditoria:

| Campo | Tipo | Descrição |
|---|---|---|
| `createdBy` | String | Usuário que criou o registro |
| `createDate` | LocalDateTime | Data de criação |
| `lastModifiedDate` | LocalDateTime | Data da última modificação |
| `modifiedBy` | String | Usuário que modificou |

---

### SystemUser — `tb_user`

Representa os operadores do sistema.

| Coluna | Tipo | Restrições |
|---|---|---|
| `user_id` | UUID | PK |
| `user_first_name` | VARCHAR(50) | NOT NULL |
| `user_last_name` | VARCHAR(50) | — |
| `user_username` | VARCHAR(30) | NOT NULL, UNIQUE |
| `user_email` | VARCHAR(100) | NOT NULL, UNIQUE |
| `user_password` | VARCHAR(255) | NOT NULL, hash BCrypt |
| `user_role` | ENUM | ADMIN ou SELLER |
| `created_at` | TIMESTAMP | NOT NULL |
| `updated_at` | TIMESTAMP | NOT NULL |
| `updated_by` | VARCHAR | — |

---

### Client — `tb_client`

Representa os clientes da distribuidora. Suporta **soft delete** via `CLI_is_active`.

| Coluna | Tipo | Restrições |
|---|---|---|
| `CLI_id` | UUID | PK |
| `CLI_name` | VARCHAR | NOT NULL |
| `CLI_phone` | VARCHAR | — |
| `CLI_address` | VARCHAR | — |
| `CLI_notes` | VARCHAR | — |
| `balance` | DECIMAL(10,2) | Saldo de crédito do cliente |
| `CLI_is_active` | BOOLEAN | Flag de exclusão lógica |
| `CLI_type` | ENUM | RETAIL ou RESELLER |
| `CLI_status` | ENUM | PAID ou OVERDUE |
| `CLI_bottles_debt` | INTEGER | Default 0 |

**Relacionamentos:**
- `OneToMany` → LoanedBottle
- `OneToMany` → Order

**Soft Delete:** O Hibernate intercepta operações de DELETE e executa `UPDATE tb_client SET CLI_is_active = false`. Todas as queries filtram automaticamente `CLI_is_active = true`.

---

### Product — `tb_product`

Catálogo de produtos (água ou gás).

| Coluna | Tipo | Descrição |
|---|---|---|
| `PROD_id` | UUID | PK |
| `PROD_name` | VARCHAR | NOT NULL |
| `PROD_basePrice` | DECIMAL(10,2) | Preço de venda base |
| `PROD_last_CostPrice` | DECIMAL(10,2) | Último preço de custo |
| `PROD_type` | ENUM | WATER ou GAS |
| `PROD_is_active` | BOOLEAN | Produto ativo? |
| `gasSupplier_id` | UUID | FK → GasSupplier (fornecedor padrão) |

---

### Stock — `tb_stock`

Controle de estoque por produto.

| Coluna | Tipo | Descrição |
|---|---|---|
| `STOCK_id` | UUID | PK |
| `PROD_id` | UUID | FK → Product |
| `STOCK_qtd` | INTEGER | Quantidade atual |
| `STOCK_minimum` | INTEGER | Quantidade mínima (alerta de estoque baixo) |

**Regras de criação:** Ao criar um produto, o estoque é criado automaticamente:
- GAS: mínimo = 0
- WATER: mínimo = 5

---

### Order — `tb_order`

Pedido realizado por um cliente.

| Coluna | Tipo | Descrição |
|---|---|---|
| `ORD_id` | UUID | PK |
| `ORD_deliveryDate` | TIMESTAMP | Data de entrega |
| `ORD_status` | ENUM | PENDING, SHIPPED, DELIVERED, CANCELLED |
| `ORD_payment_status` | ENUM | PENDING, PARTIAL, PAID (default PENDING) |
| `CLI_id` | UUID | FK → Client (NOT NULL) |

**Relacionamentos:**
- `OneToMany` → Payment (cascade ALL, orphan removal)
- `OneToMany` → OrderItem (cascade ALL, orphan removal)

**Método utilitário:** `getTotalValue()` — soma o valor de todos os itens do pedido.

---

### OrderItem — `tb_orderItem`

Item de um pedido (produto + quantidade + preço).

| Coluna | Tipo | Descrição |
|---|---|---|
| `ORD_ITEM_id` | UUID | PK |
| `ORD_ITEM_quantity` | INTEGER | NOT NULL |
| `ORD_ITEM_unitPrice` | DECIMAL(10,2) | Preço unitário no momento da venda |
| `ORD_ITEM_bottleExpiration` | DATE | Validade do vasilhame (para água) |
| `ORD_id` | UUID | FK → Order |
| `PROD_id` | UUID | FK → Product |
| `GAS_SUP_id` | UUID | FK → GasSupplier (para itens de gás) |

---

### Payment — `tb_payment`

Registro de pagamento vinculado a um pedido.

| Coluna | Tipo | Descrição |
|---|---|---|
| `PAY_id` | UUID | PK |
| `PAY_date` | TIMESTAMP | NOT NULL |
| `PAY_amount` | DECIMAL(10,2) | NOT NULL |
| `ORD_id` | UUID | FK → Order |
| `PAY_method` | ENUM | PIX, DINHEIRO, SALDO |
| `PAY_notes` | VARCHAR | Observações |

---

### GasSupplier — `tb_gasSupplier`

Fornecedores de gás cadastrados.

| Coluna | Tipo | Descrição |
|---|---|---|
| `GAS_SUP_id` | UUID | PK |
| `GAS_SUP_name` | VARCHAR | NOT NULL |
| `GAS_SUP_notes` | VARCHAR | — |

---

### GasSettlement — `tb_gasSettlement`

Registro de liquidações financeiras com fornecedores de gás.

| Coluna | Tipo | Descrição |
|---|---|---|
| `GAS_SET_id` | UUID | PK |
| `GAS_SUP_id` | UUID | FK → GasSupplier |
| `GAS_SUP_amount` | DECIMAL(10,2) | Valor da liquidação |
| `GAS_SUP_settled` | BOOLEAN | Liquidado? |
| `GAS_SUP_settledDate` | TIMESTAMP | Data de liquidação |
| `GAS_SUP_settlement_type` | ENUM | YOU_OWE ou SUPPLIER_OWE |
| `ORD_ITEM_id` | UUID | FK → OrderItem (1:1) |

---

### LoanedBottle — `tb_loanedBottle`

Controle de vasilhames emprestados a clientes.

| Coluna | Tipo | Descrição |
|---|---|---|
| `LB_id` | UUID | PK |
| `PROD_id` | UUID | FK → Product |
| `LB_qtd` | INTEGER | Quantidade emprestada |
| `CLI_id` | UUID | FK → Client |
| `LB_loanDate` | TIMESTAMP | Data do empréstimo |
| `LB_returnDate` | TIMESTAMP | Data de devolução |
| `LB_status` | ENUM | LOANED ou RETURNED |

---

### DepotBottleDebt — `tb_depot_bottle_debt`

Garrafões que **o depósito deve ao cliente**, separados dos empréstimos em
`LoanedBottle`. Herda a auditoria de `BaseEntity` e usa UUID gerado pelo JPA.

| Campo | Tipo | Regra |
|---|---|---|
| `id` | UUID | Identificador do registro |
| `client` | FK → Client | Cliente existente, obrigatório |
| `quantity` | INTEGER | Inteiro positivo, obrigatório |
| `debtDate` | LocalDateTime | Data informada pelo operador, obrigatória |
| `notes` | VARCHAR(255) | Observação opcional |
| `status` | BottleDebtStatus | `PENDING` na criação; `COMPLETED` na baixa |
| `completionDate` | LocalDateTime | Nula até a baixa; preenchida pelo servidor |

O registro permanece após a conclusão. Não altera `Client.bottlesDebt`,
`Client.balance`, empréstimos, pagamentos ou estoque. O schema é criado/atualizado
por `ddl-auto: update`, sem migrations.

---

### ClientPrice — `tb_clientPrice`

Preços personalizados por combinação cliente-produto.

| Coluna | Tipo | Descrição |
|---|---|---|
| `CP_id` | UUID | PK |
| `CLI_id` | UUID | FK → Client |
| `PROD_id` | UUID | FK → Product |
| `CP_price` | DECIMAL(10,2) | Preço customizado |

---

## 6. Enums

| Enum | Valores | Uso |
|---|---|---|
| `Role` | ADMIN, SELLER | Perfil de acesso do usuário |
| `ClientType` | RETAIL, RESELLER | Tipo de cliente |
| `ClientStatus` | PAID, OVERDUE | Situação financeira do cliente |
| `ProductType` | WATER, GAS | Categoria do produto |
| `OrderStatus` | PENDING, SHIPPED, DELIVERED, CANCELLED | Ciclo de vida do pedido |
| `PaymentStatus` | PENDING, PARTIAL, PAID | Status do pagamento do pedido |
| `Method` | PIX, DINHEIRO, SALDO | Forma de pagamento |
| `LoanStatus` | LOANED, RETURNED | Status do empréstimo de vasilhame |
| `BottleDebtStatus` | PENDING, COMPLETED | Pendência de garrafões devida pelo depósito ao cliente |
| `SettlementType` | YOU_OWE, SUPPLIER_OWE | Direção da liquidação com fornecedor |
| `StockStatus` | NORMAL, LOW_STOCK, OUT_OF_STOCK | Status do estoque (suporte a relatórios) |

---

## 7. DTOs (Data Transfer Objects)

### Request DTOs (Entrada)

| DTO | Campos principais |
|---|---|
| `UserRequestDTO` | firstName, lastName, username, email, password, role |
| `ClientRequestDTO` | name, phone, address, notes, clientType, clientStatus |
| `ProductRequestDTO` | name, basePrice, costPrice, type, defaultSupplierId |
| `OrderRequestDTOv2` | clientId, items[], deliveryDate, isDelivery, notes, initialPayments[] (opcional) |
| `InitialPaymentRequestDTO` | amount, paymentMethod (PIX, DINHEIRO ou SALDO) |
| `OrderItemRequestDTO` | productId, quantity, bottleExpiration, supplierId, gasCostPrice, receivedByUs |
| `PaymentRequestDTO` | amount, orderId, method, notes |
| `GasSupplierRequestDTO` | name, notes |
| `LoanedBottleRequestDTO` | clientId, productId, quantity, loanDate |
| `DepotBottleDebtRequestDTO` | clientId, quantity, debtDate, notes (opcional) |
| `ClientPriceRequestDTO` | productId, customPrice |

### Response DTOs (Saída)

| DTO | Campos principais |
|---|---|
| `OrderResponseDTO` | id, deliveryDate, status, paymentStatus, clientId, clientName, items[], totalValue, createDate |
| `OrderItemResponseDTO` | id, quantity, unitPrice, bottleExpiration, productId, gasSupplierId |
| `PaymentResponseDTO` | id, date, amount, orderId, method, notes |
| `OrderBalanceDTO` | orderId, totalAmount, paidAmount, debt |
| `ClientResponseDTO` | id, name, phone, address, notes, balance, clientType, clientStatus, orderCount |
| `ClientPriceResponseDTO` | id, clientId, productId, price |
| `GasSettlementResponseDTO` | id, supplierId, amount, settled, settledDate, settlementType |
| `GasSettlementReportDTO` | supplier details, settled/unsettled amounts, date range |
| `LoanedBottleResponseDTO` | id, productId, clientId, quantity, loanDate, returnDate, status |
| `DepotBottleDebtResponseDTO` | id, clientId, clientName, quantity, debtDate, notes, status, completionDate |
| `StockResponseDTO` | id, productId, quantityInStock, minimumStock |
| `FinancialReportDTO` | totalInvoiced, totalReceived, gasGrossProfit, balance |
| `DailyReportDTO` | métricas operacionais diárias |
| `InventoryFlowDTO` | movimentações de estoque |

### Update DTOs (Atualização parcial)

| DTO | Propósito |
|---|---|
| `UserUpdateDTO` | Atualização seletiva de campos do usuário |
| `ClientUpdateDTO` | Atualização seletiva de campos do cliente |
| `ProductUpdateDTO` | Atualização seletiva de campos do produto |
| `GasSupplierUpdateDTO` | Atualização seletiva do fornecedor |

---

## 8. Mappers

Todos os mappers utilizam **MapStruct** e estão em `com.vitalis.demo.mapper/`. São interfaces anotadas com `@Mapper(componentModel = "spring")`, geradas em tempo de compilação.

| Mapper | Converte |
|---|---|
| `UserMapper` | SystemUser ↔ UserRequestDTO / UserResponseDTO |
| `ClientMapper` | Client ↔ ClientRequestDTO / ClientResponseDTO |
| `ProductMapper` | Product ↔ ProductRequestDTO / ProductResponseDTO |
| `OrderMapper` | Order ↔ OrderRequestDTOv2 / OrderResponseDTO |
| `OrderItemMapper` | OrderItem ↔ OrderItemRequestDTO / OrderItemResponseDTO |
| `PaymentMapper` | Payment ↔ PaymentRequestDTO / PaymentResponseDTO |
| `StockMapper` | Stock ↔ StockResponseDTO |
| `GasSupplierMapper` | GasSupplier ↔ GasSupplierRequestDTO / GasSupplierResponseDTO |
| `GasSettlementMapper` | GasSettlement ↔ GasSettlementResponseDTO |
| `LoanedBottleMapper` | LoanedBottle ↔ LoanedBottleRequestDTO / LoanedBottleResponseDTO |
| `DepotBottleDebtMapper` | DepotBottleDebt ↔ DepotBottleDebtRequestDTO / DepotBottleDebtResponseDTO |
| `ClientPriceMapper` | ClientPrice ↔ ClientPriceRequestDTO / ClientPriceResponseDTO |

---

## 9. Repositórios

Todos estendem `JpaRepository` e ficam em `com.vitalis.demo.repository/`.

### OrderRepository

```java
findByClient(client, pageable)
findByStatus(status)
findByClientAndStatus(client, status)
findByClientAndPaymentStatusNotOrderByCreateDateAsc(client, status)  // pedidos abertos por cliente (FIFO)
findByCreateDateBetween(start, end)
findByStatusAndDeliveryDateBetween(status, start, end)
countByClientId(clientId)
// JPQL:
sumTotalAmount(status, start, end) // soma o valor dos itens dos pedidos no período
```

### ClientRepository

```java
findByNameContainingIgnoreCase(pageable, name)
findByNameContainingIgnoreCaseAndClientType(pageable, name, type)
findByClientType(pageable, type)
findByNameIgnoreCaseAndPhone(name, phone)
```

### PaymentRepository

```java
findByOrder_Id(orderId)
findByCreateDateBetween(start, end)
// JPQL:
sumTotalReceived(start, end) // soma os valores pagos no período
```

### GasSettlementRepository

```java
findByGasSupplier(supplier)
findBySettled(settled)
findBySettledFalse()
findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(supplierId, start, end)
findByOrderItem(orderItem)
// JPQL com CASE:
sumTotalProfit(start, end) // lucro líquido calculado com base no tipo de liquidação
```

---

## 10. Serviços (Business Logic)

### UserService

Gerencia o ciclo de vida dos usuários do sistema.

- `save(dto)` — cria usuário com senha encodada via BCrypt
- `findAll()` — lista todos os usuários
- `findById(id)` — busca por ID, lança `ResourceNotFoundException` se não encontrado
- `update(id, dto)` — atualização parcial via `UserUpdateDTO`
- `delete(id)` — remove o usuário

---

### ClientService

Gerencia o ciclo de vida dos clientes com regras de negócio específicas.

- `save(dto)` — cria cliente com saldo financeiro e status ativo, sem programa de pontos
- `findAll(pageable, name, type)` — lista com filtros e paginação
- `findById(id)` — busca por ID
- `update(id, dto)` — atualização parcial
- `delete(id)` — soft delete (valida se cliente tem pedidos antes de excluir)
- `addCreditBalance(client, amount)` — credita valor no saldo do cliente
- `consumeCreditBalance(client, amount)` — debita saldo (valida se há saldo suficiente)
- `calculateDebtBalance(client)` — calcula dívida total com base nos pedidos abertos

---

### ProductService

Gerencia o catálogo de produtos.

- `save(dto)` — cria produto e cria estoque inicial automaticamente
- `findAll(pageable)` — lista com paginação
- `findById(id)` — busca por ID
- `update(id, dto)` — atualização parcial
- `toggleActive(id)` — ativa/desativa produto
- `delete(id)` — remove produto

---

### StockService

Controla o inventário.

- `increaseStock(productId, quantity)` — aumenta estoque
- `decreaseStock(productId, quantity)` — reduz estoque (só valida disponibilidade para WATER)
- `checkStockAvailability(productId, quantity)` — verifica se há estoque suficiente (ignora GAS)
- `createInitialStock(product)` — cria registro de estoque ao criar produto
  - GAS → mínimo = 0
  - WATER → mínimo = 5

---

### OrderService

Gerencia o ciclo de vida dos pedidos — a service mais complexa do sistema.

- `createOrders(dto)` — cria pedidos separando itens por tipo:
  1. Divide itens em WATER e GAS
  2. Cria pedido separado para cada tipo (máximo 2 pedidos por request)
  3. Para itens GAS: gera liquidações com fornecedor
  4. Retorna lista de `OrderResponseDTO`
- `updateOrders(id, dto)` — atualiza pedido existente gerenciando substituição de itens
- `confirmDelivery(id)` — confirma entrega:
  1. Valida que pedido não está já entregue
  2. Para cada item: diminui estoque de água (gás não tem estoque local)
  3. Seta data de entrega e muda status para DELIVERED
- `updateStatus(id, status)` — muda status do pedido
- `cancelOrder(id)` — cancela pedido (somente ADMIN)
- `findOpenOrdersByClient(clientId)` — retorna pedidos não pagos do cliente (FIFO)
- `listActiveOrders()` — lista todos os pedidos com status SHIPPED
- `findByClient(clientId, pageable)` — lista pedidos de um cliente

---

### PaymentService

Gerencia registros de pagamento com lógica de distribuição automática.

- `registerPayment(dto)` — pagamento único:
  1. Se método = SALDO: valida e consome saldo do cliente
  2. Se pagamento exato/parcial: aplica diretamente, atualiza status do pedido
  3. Se pagamento maior que a dívida: quita o pedido atual + distribui o excedente via FIFO nos demais pedidos abertos do cliente
- `processBulkPayment(clientId, dto)` — pagamento em lote (FIFO):
  1. Busca todos os pedidos abertos do cliente ordenados por data
  2. Distribui o valor da esquerda para a direita até zerar
  3. Excedente vira crédito no saldo do cliente
- `findOrderBalance(orderId)` — calcula e retorna `{totalAmount, paidAmount, debt}`
- `findByOrderId(orderId)` — lista todos os pagamentos de um pedido
- `findById(id)` — busca pagamento por ID

---

### GasSupplierService

Gerencia o cadastro de fornecedores de gás.

- `save(dto)`, `findAll(pageable)`, `findById(id)`, `update(id, dto)`, `delete(id)`

---

### GasSettlementService

Gerencia as liquidações financeiras com fornecedores de gás.

- `generateReportBySupplier(supplierId, start, end)` — gera relatório de liquidações no período
- `settleAllBySupplier(supplierId, start, end)` — quita em lote todas as liquidações pendentes do fornecedor no período
- `settleIndividual(id)` — quita uma liquidação individual
- `findById(id)` — busca liquidação por ID

---

### LoanedBottleService

Controla empréstimos de vasilhames.

- `save(dto)` — registra empréstimo
- `findPendingReturns(pageable)` — lista vasilhames com status LOANED
- `findById(id)` — busca por ID
- `findPendingByClient(clientId)` — pendências de um cliente específico
- `registerReturn(id)` — registra devolução: muda status para RETURNED, seta data de retorno, decrementa `CLI_bottles_debt`
- `delete(id)` — remove registro

---

### DepotBottleDebtService

- `save(dto)` — valida quantidade e data, resolve o cliente e registra a pendência como `PENDING`.
- `findPending(clientId, pageable)` — retorna somente pendências, com filtro opcional por cliente.
- `findById(id)` — consulta o registro, inclusive após a conclusão.
- `complete(id)` — baixa integralmente, salva `COMPLETED` e a data do servidor.
  Bloqueia a linha durante a transação para impedir que duas baixas concorrentes
  sobrescrevam a data. A segunda baixa retorna erro de negócio sem alterar o registro.

As gravações são transacionais e as consultas usam `@Transactional(readOnly = true)`.
Não há baixa parcial, exclusão, acerto financeiro ou movimentação automática de estoque.

Observação sobre o legado: a descrição acima de `LoanedBottleService.registerReturn`
menciona decremento de `CLI_bottles_debt`, mas o código atual chama
`ClientService.updateBottleBalance` com quantidade positiva, que soma ao campo.
Esse comportamento foi preservado fora do escopo da issue #2. O novo recurso não
utiliza esse campo nem modifica o fluxo de devolução existente.

---

### ClientPriceService

Gerencia preços customizados por cliente.

- `save(clientId, dto)` — cria preço customizado
- `findByClient(clientId)` — lista preços do cliente
- `update(clientId, priceId, dto)` — atualiza preço
- `delete(clientId, priceId)` — remove preço customizado

---

### FinancialService

Gera relatórios financeiros e operacionais.

- `generateFinancialReport(start, end)` — `FinancialReportDTO`:
  - `totalInvoiced` — total faturado no período
  - `totalReceived` — total efetivamente recebido
  - `gasGrossProfit` — lucro bruto do gás
  - `balance` — saldo (recebido − custo)
- `findDailyFinancialPerformance(date)` — `DailyReportDTO` com snapshot do dia
- `findInventoryFlowByDate(date)` — `InventoryFlowDTO` com movimentações de estoque
- `generateOperationalSummary(start, end)` — métricas operacionais do período

---

## 11. Controllers e Endpoints

Base URL: `http://localhost:8080`

### UserController — `/users`

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `POST` | `/users` | Público | Criar usuário |
| `GET` | `/users` | Autenticado | Listar usuários |
| `GET` | `/users/{id}` | Autenticado | Buscar por ID |
| `PATCH` | `/users/{id}` | Autenticado | Atualizar parcialmente |
| `DELETE` | `/users/{id}` | Autenticado | Remover usuário |

---

### ClientController — `/clients`

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `GET` | `/clients` | Autenticado | Listar (paginado, filtro por nome e tipo) |
| `GET` | `/clients/{id}` | Autenticado | Buscar por ID |
| `POST` | `/clients` | Autenticado | Criar cliente |
| `PUT` | `/clients/{id}` | Autenticado | Atualizar cliente |
| `DELETE` | `/clients/{id}` | Autenticado | Soft delete |

---

### ProductController — `/products`

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `GET` | `/products` | Autenticado | Listar produtos (paginado) |
| `GET` | `/products/{id}` | Autenticado | Buscar por ID |
| `POST` | `/products` | **ADMIN** | Criar produto |
| `PUT` | `/products/{id}` | **ADMIN** | Atualizar produto |
| `PATCH` | `/products/{id}/toggle-active` | **ADMIN** | Ativar/desativar produto |
| `DELETE` | `/products/{id}` | **ADMIN** | Remover produto |

---

### OrderController — `/orders`

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `GET` | `/orders` | Autenticado | Listar todos (paginado, ordem DESC por data) |
| `GET` | `/orders/{id}` | Autenticado | Buscar por ID |
| `GET` | `/orders/client/{id}` | Autenticado | Pedidos de um cliente (paginado) |
| `GET` | `/orders/active` | Autenticado | Pedidos com status SHIPPED |
| `GET` | `/orders/client/{id}/open` | Autenticado | Pedidos não pagos do cliente |
| `POST` | `/orders` | Autenticado | Criar pedido(s) — separa água/gás |
| `PUT` | `/orders/{id}` | Autenticado | Atualizar pedido |
| `PATCH` | `/orders/{id}/status` | Autenticado | Mudar status (`?status=SHIPPED`) |
| `PATCH` | `/orders/{id}/confirm-delivery` | Autenticado | Confirmar entrega |
| `DELETE` | `/orders/{id}` | **ADMIN** | Cancelar pedido |

---

### PaymentController — `/payments`

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `POST` | `/payments` | Autenticado | Registrar pagamento único |
| `POST` | `/payments/bulk/{clientId}` | Autenticado | Pagamento em lote (FIFO) |
| `GET` | `/payments/orders/{orderId}/balance` | Autenticado | Saldo do pedido |
| `GET` | `/payments/orders/{orderId}` | Autenticado | Listar pagamentos do pedido |
| `GET` | `/payments/{id}` | Autenticado | Buscar por ID |

---

### StockController — `/stocks`

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `GET` | `/stocks` | Autenticado | Listar estoque (paginado) |
| `PATCH` | `/stocks/products/{productId}` | Autenticado | Ajustar quantidade (positivo = entrada, negativo = saída) |

---

### GasSupplierController — `/suppliers`

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `POST` | `/suppliers` | Autenticado | Criar fornecedor |
| `GET` | `/suppliers` | Autenticado | Listar (paginado) |
| `GET` | `/suppliers/{id}` | Autenticado | Buscar por ID |
| `PUT` | `/suppliers/{id}` | Autenticado | Atualizar |
| `DELETE` | `/suppliers/{id}` | Autenticado | Remover |

---

### GasSettlementController — `/gas-settlements`

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `GET` | `/gas-settlements/report` | **ADMIN** | Relatório (`?supplierId=&start=&end=`) |
| `GET` | `/gas-settlements/{id}` | Autenticado | Buscar por ID |
| `PATCH` | `/gas-settlements/bulk-settle` | Autenticado | Liquidar em lote (`?supplierId=&start=&end=`) |
| `PATCH` | `/gas-settlements/{id}/settle` | Autenticado | Liquidar individual |

---

### LoanedBottleController — `/bottles`

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `POST` | `/bottles` | Autenticado | Registrar empréstimo |
| `GET` | `/bottles` | Autenticado | Listar pendentes de devolução |
| `GET` | `/bottles/{id}` | Autenticado | Buscar por ID |
| `GET` | `/bottles/client/{clientId}` | Autenticado | Pendências do cliente |
| `PATCH` | `/bottles/{id}/return` | Autenticado | Registrar devolução |
| `DELETE` | `/bottles/{id}` | Autenticado | Remover registro |

---

### DepotBottleDebtController — `/depot-bottle-debts`

Todos os endpoints exigem a autenticação HTTP Basic existente, para ADMIN ou SELLER.
O recurso representa exclusivamente garrafões devidos **pelo depósito ao cliente**.
As rotas e os DTOs de `/bottles` permanecem inalterados.

| Método | Endpoint | Sucesso | Descrição |
|---|---|---|---|
| `POST` | `/depot-bottle-debts` | 201 + DepotBottleDebtResponseDTO | Registrar pendência |
| `GET` | `/depot-bottle-debts` | 200 + Page&lt;DepotBottleDebtResponseDTO&gt; | Listar somente `PENDING` |
| `GET` | `/depot-bottle-debts/{id}` | 200 + DepotBottleDebtResponseDTO | Consultar pendente ou concluído |
| `PATCH` | `/depot-bottle-debts/{id}/complete` | 204, sem corpo | Baixa integral, sem corpo de request |

Payload de criação:

```json
{
  "clientId": "00000000-0000-0000-0000-000000000001",
  "quantity": 2,
  "debtDate": "2026-11-12T09:30:00",
  "notes": "Cliente deixou os garrafões no depósito"
}
```

`clientId`, `quantity` e `debtDate` são obrigatórios. `quantity` deve ser inteiro
entre 1 e 2147483647; valores fracionários não são truncados. `notes` pode ser
omitida, nula ou vazia e aceita até 255 caracteres. As datas usam ISO 8601 local,
sem offset, como nos demais DTOs com `LocalDateTime`. `status` e `completionDate`
são controlados pelo servidor, não fazem parte do request.

Resposta de criação (a mesma estrutura é usada na consulta por ID e em `content`):

```json
{
  "id": "00000000-0000-0000-0000-000000000002",
  "clientId": "00000000-0000-0000-0000-000000000001",
  "clientName": "Maria",
  "quantity": 2,
  "debtDate": "2026-11-12T09:30:00",
  "notes": "Cliente deixou os garrafões no depósito",
  "status": "PENDING",
  "completionDate": null
}
```

Após a baixa, o registro continua disponível por ID com `status: "COMPLETED"`
e `completionDate` preenchida; os demais campos são preservados. Ele deixa a
listagem de pendências. Repetir a baixa retorna 400 e mantém a primeira data.

Listagem: `GET /depot-bottle-debts?clientId={uuid}&page=0&size=10&sort=debtDate,asc`.
`clientId` é opcional; quando presente, deve identificar cliente existente.
Paginação `Page<T>` do Spring, com `content`, `totalElements`, `totalPages`,
`number` e `size`; `page` começa em zero. Padrões: página 0, tamanho 10 e
ordenação por `debtDate` crescente. Para a segunda página, use `page=1`;
é possível usar `sort=debtDate,desc`. Sem resultados, retorna 200 com `content: []`.

Erros para integração:

| HTTP | Situação |
|---|---|
| 422 | Campos obrigatórios ausentes/nulos, quantidade zero/negativa ou observação com mais de 255 caracteres |
| 400 | Quantidade fracionária/fora do intervalo de Integer, JSON/UUID/data inválidos ou baixa repetida |
| 404 | Cliente inexistente no cadastro/filtro; pendência inexistente na consulta/baixa |
| 401 | Ausência ou falha da autenticação HTTP Basic existente |

Validações 422 usam o handler global com `timestamp`, `status`, `error` e
`errors` (lista de campos e mensagens). Erros de negócio e de recurso não
encontrado usam o handler global com `timestamp`, `status`, `error`, `message`
e `path`, conforme os exemplos a seguir. Erros de desserialização 400 são
tratados pelo Spring; seu corpo não deve ser usado como contrato de validação.

Exemplo de validação (422):

```json
{
  "timestamp": "2026-11-12T09:30:00",
  "status": 422,
  "error": "Erro de validação",
  "errors": [{ "field": "quantity", "message": "A quantidade deve ser maior que zero!" }]
}
```

Exemplo de baixa repetida (400):

```json
{
  "timestamp": "2026-11-12T09:30:00",
  "status": 400,
  "error": "Erro de negócio",
  "message": "A pendência de garrafões já foi baixada!",
  "path": "/depot-bottle-debts/00000000-0000-0000-0000-000000000002/complete"
}
```

Não há campos novos em responses existentes. O desktop da issue
`gabsiq73/vitalis-desktop#7` deve usar `DepotBottleDebtResponseDTO` e as novas
rotas; não deve somar essas quantidades ao saldo de empréstimos ou ao financeiro.

---

### ClientPriceController — `/clients/{clientId}/prices`

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `POST` | `/clients/{clientId}/prices` | **ADMIN** | Criar preço customizado |
| `PUT` | `/clients/{clientId}/prices/{id}` | **ADMIN** | Atualizar preço |
| `DELETE` | `/clients/{clientId}/prices/{id}` | **ADMIN** | Remover preço |
| `GET` | `/clients/{clientId}/prices` | Autenticado | Listar preços do cliente |

---

### FinancialReportController — `/reports`

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `GET` | `/reports/performance` | **ADMIN** | Relatório financeiro por período (`?start=&end=`) |
| `GET` | `/reports/performance/daily` | **ADMIN** | Relatório diário (`?date=`) |
| `GET` | `/reports/inventory` | **ADMIN** | Fluxo de estoque (`?date=`) |
| `GET` | `/reports/operational` | **ADMIN** | Resumo operacional por período (`?start=&end=`) |

---

## 12. Segurança

### Autenticação

O sistema usa **HTTP Basic Authentication** — cada request deve conter o header `Authorization: Basic <base64(username:password)>`.

### Autorização

Dois níveis de acesso:

| Role | Permissões |
|---|---|
| **SELLER** | Acesso a todos os endpoints autenticados |
| **ADMIN** | Acesso pleno, incluindo operações de criação/exclusão de produtos, cancelamento de pedidos, preços customizados e relatórios financeiros |

### Endpoints Públicos

- `POST /users/**` — cadastro de usuário
- `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` — documentação da API

### CORS

Configurado para aceitar origem `http://localhost:5173` (aplicação frontend em Vite/Electron), com métodos `GET, POST, PUT, DELETE, OPTIONS`.

### Auditoria Automática

Via `AuditConfig`, os campos `createdBy` e `modifiedBy` de `BaseEntity` são preenchidos automaticamente com o nome de usuário autenticado (ou `"SISTEMA"` se não houver autenticação).

---

## 13. Tratamento de Erros

O `GlobalExceptionHandler` intercepta exceções e retorna respostas padronizadas:

| Exceção | Status HTTP | Quando ocorre |
|---|---|---|
| `MethodArgumentNotValidException` | 422 Unprocessable Entity | Validação de campos (Bean Validation) |
| `ResourceNotFoundException` | 404 Not Found | Entidade não encontrada por ID |
| `BusinessException` | 400 Bad Request | Regras de negócio violadas |
| `OutOfStockException` | 400 Bad Request | Estoque insuficiente para o pedido |

**Formato da resposta de erro:**

```json
{
  "timestamp": "2024-01-01T10:00:00",
  "status": 400,
  "message": "Descrição do erro",
  "uri": "/orders"
}
```

---

## 14. Fluxos de Negócio

### Descontinuação do programa de fidelidade — issue #3

O programa foi retirado das operações. O inventário e o tratamento são:

| Referência anterior | Tratamento |
|---|---|
| `ClientFidelity`, `ClientFidelityRepository`, relação `Client.fidelity` | Entidade, repositório e associação JPA removidos |
| `ClientMapper`, `ClientResponseDTO` | Removidos `fidelityPoints` e `pendingBonusWater` |
| `SystemConfig`, `SystemConfigDTO`, `SystemConfigService`, `/config` | Removidos `pointsPerWaterItem` e `pointsPerFreeWater`; permanece `pickupDiscountCents` |
| `PATCH /clients/{id}/add-fidelity-points`, `ClientService.addPointsFidelity` | Operação removida; a rota não existe mais |
| Criação/edição de pedidos | Sem previsão de pontos ou consumo de bônus; zero em novo item usa o preço normal |
| Confirmação de entrega | Sem concessão de pontos; mantém baixa de estoque de água |
| Cancelamento | Sem restauração/estorno de pontos ou bônus; mantém estoque, pagamentos, crédito e acertos |
| Desktop coordenado, issue `vitalis-desktop#8` | Remove telas, modais, exportações e tipos de pontos; rascunho antigo exige revisão |

Contratos atuais:

- `ClientResponseDTO`: `id`, `name`, `phone`, `address`, `notes`, `balance`,
  `clientType`, `clientStatus`, `orderCount`. `balance` continua financeiro.
- `GET /config` e `PATCH /config`: `{ "pickupDiscountCents": 125 }`.
  O desconto é informado em centavos. As configurações de pontos deixam de ser retornadas.
- Não há campo novo em responses. A remoção dos quatro campos acima e da rota
  de adição de pontos é incompatível com o desktop antigo; atualize os dois
  repositórios juntos, com a aplicação fechada durante a troca.
- `POST /orders` continua retornando lista e preserva parcelas iniciais/FIFO/SALDO.
  Preço manual positivo prevalece; `unitPrice` nulo ou não positivo resolve preço
  por cliente, revenda ou base e o desconto de retirada conforme as regras existentes.
- Pedidos históricos preservam seus valores gravados, inclusive itens a zero,
  nas consultas, entrega e cancelamento. Na edição de um pedido pendente,
  reenviar um item histórico a zero com o mesmo produto e quantidade mantém seu
  preço. Cada item histórico só pode justificar um item a zero; duplicar o item
  ou alterar a quantidade aplica o preço normal à parte nova. Não existe novo resgate.

#### Bancos existentes: procedimento sem exclusão de dados

Mantenha `ddl-auto: update`. Não há migrations nem remoção automática de tabelas,
colunas, clientes, pedidos ou pagamentos. Em bancos novos, o Hibernate não cria
`tb_client_fidelity`, `tb_client.cli_fidelity_id` nem os dois campos de pontos
de `tb_system_config`. Em bancos existentes, eles permanecem fisicamente e não
são mais lidos/escritos pela aplicação; a FK antiga pode permanecer.

Antes de atualizar um banco existente, com API e desktop parados:

1. Faça backup do PostgreSQL e confira se as colunas antigas de configuração existem:

   ```sql
   SELECT column_name, is_nullable, column_default
   FROM information_schema.columns
   WHERE table_schema = 'public' AND table_name = 'tb_system_config'
     AND column_name IN ('points_per_water_item', 'points_per_free_water');
   ```

2. Se as duas colunas existirem, execute a preparação abaixo. Elas eram `NOT NULL`
   sem default; os defaults permitem inserir a configuração operacional caso a
   tabela esteja vazia, sem voltar a mapear os campos obsoletos. Nenhum valor de
   linha existente é modificado. Em banco novo, não execute este trecho.

   ```sql
   BEGIN;
   ALTER TABLE tb_system_config ALTER COLUMN points_per_water_item SET DEFAULT 1;
   ALTER TABLE tb_system_config ALTER COLUMN points_per_free_water SET DEFAULT 10;
   COMMIT;
   ```

3. Atualize backend e desktop coordenados e inicie com `DB_PORT` conforme README.
   Confira `/config`, cadastro/consulta de clientes, leitura de pedido histórico
   a zero e criação/entrega/cancelamento de pedido com preço especial e SALDO.
4. Preserve tabela, associação física e valores antigos para auditoria/retorno à
   versão anterior. Não é necessário executar `DROP`, apagar pontos nem recalcular
   valores históricos. Qualquer limpeza física futura é uma tarefa separada,
   precedida de backup e revisão das dependências do banco.

O teste `LoyaltyRemovalTest` inicializa um schema legado representativo com
`ddl-auto: update`, mantém os registros antigos de pontos e verifica inserções
sem a associação, bootstrap de configuração, preços, histórico, estoque e SALDO.

### Criação de Pedido

```
1. Cliente envia OrderRequestDTOv2 com lista de itens (água + gás misturado)
2. OrderService separa itens por tipo (WATER, GAS)
3. Para cada tipo → cria um Order separado (máximo 2 pedidos por request)
4. Para itens GAS → cria GasSettlement para cada item (controle de liquidação com fornecedor)
5. Retorna lista de OrderResponseDTO
```

#### Pagamentos iniciais na criação (`POST /orders`)

O campo opcional `initialPayments` registra as parcelas junto com todos os subpedidos
em uma única transação. O retorno continua sendo uma lista de `OrderResponseDTO`,
com os status financeiros finais, sem campos novos. Omitir o campo, enviar `null`
ou uma lista vazia preserva o comportamento anterior. O campo pertence à criação;
`PUT /orders/{id}` não registra pagamentos iniciais. Pagamentos de pedidos existentes
continuam em `POST /payments` e `POST /payments/bulk/{clientId}`.

```json
{
  "clientId": "00000000-0000-0000-0000-000000000001",
  "items": [{ "productId": "00000000-0000-0000-0000-000000000002", "quantity": 1 }],
  "isDelivery": true,
  "initialPayments": [
    { "amount": 10.00, "paymentMethod": "PIX" },
    { "amount": 20.00, "paymentMethod": "SALDO" }
  ]
}
```

- Cada parcela exige valor positivo em `BigDecimal`, normalizado para duas casas
  com `HALF_UP`, e um dos métodos do enum `Method`. Valores abaixo de R$ 0,01,
  métodos ausentes e entradas nulas são inválidos. O servidor verifica o saldo
  disponível ao consumir `SALDO`; esse consumo não representa entrada de caixa.
- As parcelas incluem o gás recebido pelo depósito. Antes de distribuir uma parcela,
  o servidor desconta os pagamentos automáticos de gás **do mesmo método**. Exemplo:
  água de R$ 30 e gás de R$ 70 recebido em DINHEIRO podem enviar DINHEIRO de R$ 80
  e PIX de R$ 20: o gás mantém seu pagamento automático de R$ 70 e a água recebe
  R$ 10 em DINHEIRO mais R$ 20 em PIX. Não se registra novamente o pagamento do gás.
  Quando a lista é informada, ela deve cobrir todo o gás automático no método correto;
  inconsistências revertem a criação. Gás recebido pelo entregador não integra as
  parcelas recebidas no depósito e continua quitado conforme `OrderBalanceService`.
- Primeiro são abatidas as dívidas dos novos subpedidos, calculadas pelo
  `OrderBalanceService`, sem assumir qual posição da resposta representa água ou gás.
  Apenas o excedente segue para os demais pedidos abertos do cliente por FIFO;
  o restante vira crédito, mantendo o registro de caixa para PIX/DINHEIRO.
- `OrderCreationService` coordena os serviços existentes na transação externa,
  sem dependência circular entre `OrderService` e `PaymentService`. A retirada
  preserva o cálculo de preço original e confirma a entrega depois dos pagamentos,
  para consumir o crédito antes do recálculo da dívida. Qualquer falha reverte pedidos,
  pagamentos, crédito consumido/gerado, acertos e estoque.
- Empréstimos de vasilhames continuam separados da transação. Se o empréstimo falhar
  após a criação, o desktop informa que pedido e pagamentos foram registrados e
  orienta registrar o empréstimo pela tela de vasilhames, sem tentar anular o pedido.

Observações da documentação legada: a configuração atual usa `ddl-auto: update`
e exige `DB_PORT`; gás não cria estoque local. As descrições anteriores de
`create-drop`, porta fixa e criação de estoque de gás não refletem o código atual;
esses comportamentos foram preservados nesta issue.

### Confirmação de Entrega

```
1. PATCH /orders/{id}/confirm-delivery
2. Valida que o pedido não está com status DELIVERED
3. Para cada OrderItem:
   a. StockService.decreaseStock(productId, quantity)
4. Order.deliveryDate = LocalDateTime.now()
5. Order.status = DELIVERED
```

### Pagamento Único com FIFO

```
1. POST /payments com { orderId, amount, method }
2. Se method = SALDO → verifica client.balance >= amount, debita o saldo
3. Calcula dívida atual do pedido (total − já pago)
4. Se amount <= dívida → pagamento parcial/exato, atualiza paymentStatus
5. Se amount > dívida:
   a. Quita o pedido atual
   b. Excedente = amount − dívida do pedido atual
   c. Busca outros pedidos abertos do cliente (ordem crescente por data — FIFO)
   d. Distribui excedente pedido a pedido até zerar
   e. Se sobrar → adiciona ao client.balance como crédito
```

### Pagamento em Lote (Bulk)

```
1. POST /payments/bulk/{clientId} com { amount, method }
2. Busca todos os pedidos abertos do cliente (FIFO)
3. Para cada pedido (na ordem):
   a. Calcula quanto falta para quitar
   b. Aplica o disponível
   c. Atualiza paymentStatus
   d. Deduz do total disponível
4. Se sobrar valor → client.balance += excedente
```

### Liquidação de Gás (Gas Settlement)

```
1. Ao criar OrderItem de GAS → GasSettlementService cria GasSettlement
   - YOU_OWE: distribuidora deve ao fornecedor (custo do gás)
   - SUPPLIER_OWE: fornecedor deve à distribuidora (comissão/margem)
2. PATCH /gas-settlements/bulk-settle?supplierId=X&start=Y&end=Z
   → Marca todas as liquidações pendentes do fornecedor no período como settled
3. GET /gas-settlements/report → relatório consolidado (valores devidos / a receber)
```
