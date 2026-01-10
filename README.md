# Vitalis 
-Sistema de Controle de Vendas – Água e Gás

Este projeto é um sistema simples de controle de vendas de água e gás, desenvolvido para uso em um pequeno comércio familiar.  
Além de atender a uma necessidade real, o projeto também tem como objetivo servir como experiência prática de desenvolvimento de um aplicativo do zero.

---

##  Objetivo do Sistema

O sistema tem como finalidade:

- Registrar vendas de água e gás
- Controlar pagamentos (à vista, fiado ou parciais)
- Acompanhar clientes devedores
- Controlar pendências relacionadas ao gás (acertos com fornecedores)
- Auxiliar no controle administrativo do comércio
- Gerar relatórios simples de vendas e pagamentos

---

##  Visão Geral da UML

O sistema foi modelado utilizando UML para facilitar o entendimento antes da implementação.  
A UML representa as principais entidades do negócio e como elas se relacionam.

As principais entidades são:

- **Client**
- **Order**
- **OrderItem**
- **Payment**
- **Product**
- **GasSupplier**
- **GasSettlement**

---

##  Entidades Principais

### Client
Representa um cliente do comércio.

Responsabilidades:
- Armazenar dados básicos do cliente
- Permitir identificar clientes com pendências financeiras ou operacionais
- Servir como base para histórico de pedidos e pagamentos

---

### Order
Representa um pedido realizado por um cliente.

Responsabilidades:
- Agrupar os itens vendidos
- Manter o valor total do pedido
- Servir como base para controle de pagamentos
- Permitir identificar pedidos pagos ou em aberto

---

### OrderItem
Representa um item dentro de um pedido.

Responsabilidades:
- Indicar o produto vendido
- Indicar a quantidade
- Definir o valor unitário no momento da venda
- Servir de base para relatórios de vendas

---

### Product
Representa um produto vendido no comércio (água, gás, garrafão, etc).

Responsabilidades:
- Armazenar informações básicas do produto
- Diferenciar tipos de produtos (água, gás, outros)
- Permitir análise de quais produtos são mais vendidos

---

### Payment
Representa um pagamento realizado por um cliente.

Responsabilidades:
- Registrar valores pagos
- Permitir pagamentos parciais
- Auxiliar no controle de clientes devedores
- Servir de base para relatórios financeiros

---

### GasSupplier
Representa um fornecedor de gás.

Responsabilidades:
- Identificar o fornecedor do gás vendido
- Servir de base para controle de acertos financeiros
- Permitir acompanhar pendências relacionadas ao gás

---

### GasSettlement
Representa um acerto financeiro relacionado ao gás.

Responsabilidades:
- Registrar valores que precisam ser acertados
- Indicar se o acerto já foi realizado
- Auxiliar no controle financeiro com fornecedores de gás

---

##  Funcionalidades do Sistema

- Cadastro de clientes
- Registro de pedidos
- Registro de pagamentos
- Controle automático de clientes devedores
- Controle de pendências de gás
- Área administrativa para acompanhamento geral
- Relatórios simples de vendas e pagamentos

---

## Conceito Importante

O sistema **não depende de anotações manuais** para controle de dívidas ou pendências.  
Essas informações são **derivadas automaticamente** a partir dos pedidos, itens e pagamentos registrados.

---

##  Tecnologias

As tecnologias utilizadas serão definidas durante a implementação do projeto, priorizando simplicidade e aprendizado.

---

##  Equipe

Projeto desenvolvido por dois estudantes como forma de aprendizado prático e aplicação real em um comércio local.

---

##  Observação Final

Este projeto foi pensado para ser simples, funcional e evolutivo.  
Novas funcionalidades podem ser adicionadas conforme a necessidade do comércio e o avanço do aprendizado dos desenvolvedores.
