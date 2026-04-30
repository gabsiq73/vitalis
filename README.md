# 💧 Vitalis - Gestão Comercial para Depósito de Água e Gás

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.0-6DB33F?style=for-the-badge&logo=spring-boot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)
![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)

## 📌 Sobre o Projeto

**Vitalis** é um sistema de gestão comercial desenvolvido para atender às necessidades reais e complexas de uma empresa do ramo de distribuição de água e gás. O projeto foca em automatizar operações diárias, desde o controle de estoque de água até o repasse de pagamentos de gás (que opera sob um modelo logístico sem estoque local), incluindo um robusto sistema financeiro para gestão de fiados e créditos de clientes.

Este sistema foi projetado mapeando minuciosamente o ambiente de trabalho real da empresa, traduzindo processos manuais para regras de negócio sólidas no backend.

## 🚀 Principais Funcionalidades

O sistema conta com fluxos de negócios altamente customizados:

* **Gestão de Pedidos (Orders):** * Algoritmo inteligente no `OrderService` que particiona pedidos de água e gás.
    * Como a empresa atua como revendedora de gás sem estoque físico, o sistema gerencia a comunicação direta com a distribuidora.
* **Acerto de Contas (Gas Settlement):** Lógica dedicada para o proprietário prestar contas de forma precisa com os entregadores de gás.
* **Gestão Financeira e de Clientes:**
    * Controle de **fiados** (dívidas) e gestão de **garrafões emprestados**.
    * **Sistema de Saldo de Crédito:** Se um cliente paga um valor a mais e o entregador não tem troco, o valor excedente é convertido em crédito para compras futuras.
* **Processamento de Pagamentos (FIFO):** O `PaymentService` registra pagamentos e utiliza a lógica *First-In, First-Out* (FIFO) para abater dívidas. Pagamentos recebidos quitam automaticamente os pedidos mais antigos primeiro.
* **Relatórios Financeiros:** Geração de relatórios detalhados de vendas, filtrados por métodos de pagamento e performance.
* **Controle de Estoque:** Monitoramento em tempo real do estoque de água.

## 🛠️ Tecnologias e Arquitetura

O projeto foi construído seguindo o padrão **MVC** com uma arquitetura em camadas bem definida, priorizando o conceito de *Fat Service, Skinny Controller* para manter as regras de negócio isoladas.

* **Linguagem:** Java 21
* **Framework:** Spring Boot 3.4.0
* **Banco de Dados:** PostgreSQL
* **Mapeamento de Objetos:** MapStruct
* **Boilerplate:** Lombok
* **Documentação da API:** Swagger / OpenAPI
* **Segurança:** Spring Security
* **Testes:** JUnit / Mockito
* **Testes de API:** Postman

### Estrutura de Diretórios
A arquitetura reflete boas práticas de separação de responsabilidades:
```text
src/main/java/com/vitalis/demo
 ├── config            # Configurações gerais (Swagger, Beans)
 ├── controller        # Endpoints da API (Skinny Controllers)
 ├── dto               # Objetos de Transferência (Request, Response, Update)
 ├── handler           # Tratamento global de exceções
 ├── infra.exception   # Exceções personalizadas
 ├── mapper            # Interfaces do MapStruct
 ├── model             # Entidades de domínio (JPA)
 ├── repository        # Interfaces de acesso ao banco de dados
 ├── security          # Configurações de autenticação e autorização
 ├── service           # Regras de negócio complexas (Fat Services)
 └── validator         # Validações customizadas
```

## 👨‍💻 Autores

- Felipe Damasceno
- Gabriel Siqueira

---
