# Vitalis — API de gestão para depósito de água e gás

Backend do Vitalis, um ERP local feito para a operação de uma distribuidora de água e gás. Ele traduz o fluxo real do depósito em regras de negócio: pedidos, entregas, estoque de água, fiado, pagamentos, vasilhames e acertos com fornecedores de gás.

O projeto é voltado ao uso local do negócio; não há objetivo de hospedá-lo como um serviço público.

## Funcionalidades

- Clientes de varejo, revenda e clientes avulsos, incluindo preço específico por cliente/produto.
- Produtos de água e gás, fornecedores de gás e estoque de água.
- Pedidos com entrega ou retirada. Pedidos mistos são separados em subpedidos de água e gás.
- Pagamentos por PIX, dinheiro e saldo de crédito; pagamentos excedentes podem quitar pedidos abertos em FIFO e gerar crédito para o cliente.
- Empréstimo e devolução de vasilhames.
- Acertos financeiros com fornecedores de gás: custo devido ao fornecedor (`YOU_OWE`) ou margem a receber (`SUPPLIER_OWE`).
- Relatórios financeiros, operacionais, de estoque, pagamentos diários e fiados.
- Usuários com autenticação HTTP Basic e papéis `ADMIN` e `SELLER`.

> A lógica de fidelidade ainda existe no código atual, mas está planejada para remoção. Não expanda esse fluxo.

## Stack

- Java 21, Spring Boot 3.4 e Maven Wrapper
- Spring Data JPA / Hibernate e PostgreSQL 16
- MapStruct, Lombok e Spring Security
- Springdoc / Swagger UI
- JUnit 5, Mockito e H2 nos testes

## Estrutura

```text
src/main/java/com/vitalis/demo/
├── controller/       # Endpoints REST; sem regra de negócio complexa
├── service/          # Regras, transações e orquestração dos fluxos
├── model/            # Entidades JPA e enums do domínio
├── repository/       # Acesso aos dados com Spring Data
├── dto/              # DTOs de request, response e update
├── mapper/           # Conversões MapStruct
├── security/         # UserDetails e handlers de segurança
├── config/           # Segurança e auditoria
├── handler/          # Tratamento global de exceções
└── validator/        # Validações adicionais do domínio
```

## Pré-requisitos

- JDK 21
- Docker Desktop, para o PostgreSQL local

## Executando localmente

1. Crie a rede Docker, apenas na primeira execução:

   ```powershell
   docker network create vitalis-network
   ```

2. Suba o PostgreSQL e o pgAdmin:

   ```powershell
   docker compose up -d
   ```

   O banco está em `localhost:5434`; banco, usuário e senha são `vitalis` / `postgres` / `postgres`. O pgAdmin fica em `http://localhost:5050`.

3. Informe a porta do banco e inicie a API:

   ```powershell
   $env:DB_PORT = '5434'
   .\mvnw.cmd spring-boot:run
   ```

   A API inicia em `http://localhost:8080` e o Swagger em `http://localhost:8080/swagger-ui/index.html`.

## Testes

```powershell
.\mvnw.cmd test
```

Os testes de maior cobertura estão nos serviços de pedidos, pagamentos e acertos de gás. Ao alterar regra financeira, de estoque ou de status, cubra também cancelamento e efeitos de rollback quando aplicável.

## Principais recursos HTTP

| Recurso | Base | Responsabilidade |
| --- | --- | --- |
| Clientes | `/clients` | Cadastro, avulsos, dívida e preços especiais |
| Pedidos | `/orders` | Criação, entrega, status, edição e cancelamento |
| Pagamentos | `/payments` | Pagamento individual, FIFO e caixa diário |
| Produtos / Estoque | `/products`, `/stocks` | Catálogo, ativação e quantidade em estoque |
| Vasilhames | `/bottles` | Empréstimos, devoluções e pendências |
| Gás | `/suppliers`, `/gas-settlements` | Fornecedores e acertos financeiros |
| Relatórios | `/reports` | Financeiro, operacional e fluxo de estoque |
| Administração | `/users`, `/config` | Usuários e configurações operacionais |

As rotas usam `Page<T>` do Spring quando a listagem é paginada. Consulte os controllers e o Swagger para os parâmetros e formatos exatos.

## Regras de domínio importantes

- A baixa de estoque acontece na confirmação de entrega, não na criação do pedido.
- Cancelar um pedido entregue reverte os efeitos aplicáveis; não substitua o cancelamento por exclusão física.
- Um pedido de água e gás pode resultar em mais de um pedido persistido.
- Saldo de crédito e dívida são conceitos diferentes; pagamentos com método `SALDO` consomem crédito existente.
- Acerto de gás representa a relação financeira com a fornecedora, não estoque local.

## Projeto desktop

O cliente Electron/React está no repositório [vitalis-desktop](https://github.com/gabsiq73/vitalis-desktop) e espera esta API em `http://localhost:8080`.

## Autores

- Felipe Damasceno
- Gabriel Siqueira
