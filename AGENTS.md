# Instruções para agentes — Vitalis API

## Propósito e escopo

Este é o backend local do Vitalis, um ERP específico para uma distribuidora de água e gás. Preserve as regras de negócio existentes; não generalize o sistema como e-commerce, SaaS, multitenancy ou API pública.

Antes de editar, leia a entidade, o service, o controller, os DTOs e os testes relacionados ao fluxo. Em fluxos de dinheiro e estoque (pedido, pagamento FIFO, acerto de gás), leia o service correspondente inteiro e avalie também os efeitos de cancelamento. Faça apenas a mudança pedida, sem refatorações paralelas.

## Arquitetura existente — siga-a

- A aplicação usa `controller` -> `service` -> `repository`.
- Controllers são finos: recebem HTTP, validam DTOs, chamam o service e devolvem DTOs/`ResponseEntity`.
- Services concentram regras e orquestração; use `@Transactional` em fluxos que alteram estado e `@Transactional(readOnly = true)` nas consultas, seguindo o padrão do arquivo.
- Entidades JPA ficam em `model`; repositórios Spring Data em `repository`.
- Requests, responses e updates usam DTOs em `dto/request`, `dto/response` e `dto/update`.
- Conversões entity/DTO ficam nos mappers MapStruct, não espalhadas manualmente pelos controllers.
- Use `BusinessException`, `ResourceNotFoundException` e `OutOfStockException`; deixe o handler global definir a resposta HTTP.
- O projeto usa Lombok com injeção por construtor. Siga o estilo predominante da classe alterada.
- Valores monetários usam `BigDecimal` com escala 2 e arredondamento `HALF_UP`.
- O schema usa `ddl-auto: update`; não crie migrations.

Não introduza CQRS, DDD com novas camadas, factories genéricas, event sourcing, records como entidades JPA, um framework novo de validação ou um padrão de repositório adicional sem solicitação explícita.

## Convenções de API

- As rotas atuais não têm prefixo de versão. Não crie `/api/v1` isoladamente.
- Para listagens, preserve `Page<T>`, `Pageable` e `@PageableDefault` adotados pelos controllers.
- Use `UUID` para IDs e os enums existentes para status e tipos.
- Preserve os campos existentes nos DTOs de response; apenas adicione campos. Se um contrato mudar, atualize o desktop na mesma tarefa ou informe a incompatibilidade.
- `POST /users/**` é público para viabilizar o cadastro inicial. Não altere Security/Basic Auth.

## Regras críticas do domínio

- Produtos de água usam estoque físico; a baixa ocorre em `confirmDelivery`, não na criação.
- Pedidos com água e gás são particionados e podem retornar múltiplos pedidos.
- Gás não representa estoque local. `YOU_OWE` é custo devido à fornecedora; `SUPPLIER_OWE` é margem a receber.
- Pagamentos excedentes são distribuídos entre pedidos em aberto por FIFO; o restante pode virar crédito do cliente.
- Pagamento com `SALDO` consome crédito e não é nova entrada de dinheiro.
- Cancelamento deve manter consistência de pagamentos, crédito, estoque, acertos de gás e relatórios.
- Fidelidade é legado e está planejada para remoção. Não crie funcionalidade nova apoiada nela.

## Testes e verificação

- Não rode nem corrija a suíte inteira: há 29 falhas pré-existentes em `VitalisApplicationTests`, `ClientPriceServiceTest`, `ClientServiceTest` e partes de `OrderServiceTest`, `PaymentServiceTest` e `GasSettlementServiceTest`. Esses testes antigos são candidatos a remoção.
- Para cada mudança de comportamento, crie testes novos apenas para o comportamento alterado (classes novas ou testes que já passam) e rode só eles com `.\mvnw.cmd -Dtest=NomeDaClasse test -q`.
- Cubra valor zero, pagamento parcial/excedente, pedido misto, cancelamento e estado já concluído quando pertinentes.
- Não altere `data/` nem use dados locais como atalho para provar uma mudança.

## Configuração local

- Use Java 21: `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`. Com Java 25, o Mockito falha ao criar mocks.
- Use comandos diretos; o proxy `rtk` é bloqueado pela política do Windows neste ambiente.
- PostgreSQL local é fornecido por `docker-compose.yml`, normalmente na porta `5434`.
- A aplicação exige `DB_PORT`; não fixe porta no código para contornar a configuração.
- O frontend Electron espera a API em `http://localhost:8080`.

Ao terminar cada tarefa, liste os arquivos criados/alterados e os campos novos ou mudanças de contrato em DTOs de response e endpoints para o frontend.
