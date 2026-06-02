# Vitalis — Frontend Prompt para Google Stitch

> Copie e cole o bloco abaixo diretamente no Google Stitch.

---

## PROMPT

Crie um sistema web completo chamado **Vitalis** para uma distribuidora de **água e gás**. O sistema é usado internamente por vendedores e administradores no dia a dia da operação. Use um visual moderno e limpo, com sidebar de navegação fixa à esquerda, paleta de cores baseada em azul-escuro e branco, tipografia clara, cards com sombra sutil e tabelas responsivas.

A autenticação é via usuário e senha (HTTP Basic). Existem dois perfis: **ADMIN** (acesso total) e **SELLER** (acesso limitado — não vê relatórios financeiros nem gerencia produtos/preços). Implemente controle de visibilidade de menu e botões com base no perfil.

---

### TELA 1 — Login

**Rota:** `/login`

Tela de autenticação simples. Campos: **Usuário** e **Senha**. Botão "Entrar". Em caso de erro, exibe mensagem "Credenciais inválidas". Após o login bem-sucedido, redireciona para o Dashboard. O token/sessão deve ser armazenado para uso nas requisições seguintes (HTTP Basic codificado em Base64 no header `Authorization`).

---

### TELA 2 — Dashboard (Visão Geral)

**Rota:** `/dashboard`
**Auth:** Todos

Página inicial após o login. Exibe um resumo em cards no topo:

- **Pedidos Ativos** — total de pedidos com status `SHIPPED`
- **Vasilhames Pendentes** — total de vasilhames ainda não devolvidos
- **Clientes em Atraso** — clientes com `status = OVERDUE`
- **Estoque Crítico** — produtos com quantidade abaixo do mínimo

Abaixo dos cards, uma tabela com os **últimos pedidos criados** (colunas: ID, Cliente, Total, Status, Data). Cada linha é clicável e leva ao detalhe do pedido.

---

### TELA 3 — Clientes

**Rota:** `/clients`
**Auth:** Todos

Lista paginada de clientes ativos. Barra de busca por nome. Filtro por tipo (`RETAIL` / `RESELLER`). Colunas da tabela: Nome, Telefone, Tipo, Status (`PAID` / `OVERDUE` com badge colorido), Saldo de Crédito, Ações.

Ações por linha: **Ver**, **Editar**, **Excluir** (com confirmação).

Botão "Novo Cliente" abre modal com formulário:
- Nome (obrigatório)
- Telefone
- Endereço
- Observações
- Tipo (RETAIL / RESELLER)
- Status (PAID / OVERDUE)

---

### TELA 4 — Detalhe do Cliente

**Rota:** `/clients/:id`
**Auth:** Todos

Página dividida em seções:

**Cabeçalho:** Nome, tipo, status (badge), telefone, endereço, saldo de crédito, total de dívida (calculado dos pedidos abertos), pontos de fidelidade, vasilhames pendentes de devolução.

**Aba: Pedidos** — tabela com todos os pedidos do cliente (ID, Data, Total, Status, Status Pagamento). Clique leva ao detalhe do pedido.

**Aba: Pagamentos em Lote** — campo de valor + método de pagamento. Botão "Pagar em Lote" distribui o valor automaticamente em todos os pedidos abertos (FIFO).

**Aba: Preços Customizados** *(só ADMIN)* — tabela com produto + preço customizado. Botão para adicionar, editar e remover preços.

**Aba: Fidelidade** — exibe pontos acumulados e bônus de água pendentes. Botão "Adicionar Pontos" (abre modal com campo de quantidade).

**Aba: Vasilhames** — lista vasilhames emprestados pendentes de devolução deste cliente (produto, quantidade, data do empréstimo). Botão "Registrar Devolução" por item.

---

### TELA 5 — Pedidos

**Rota:** `/orders`
**Auth:** Todos

Lista paginada de todos os pedidos, ordenada do mais recente para o mais antigo. Filtros: status do pedido, status do pagamento. Colunas: ID, Cliente, Total, Status, Status Pagamento, Data de Criação, Ações.

Ações: **Ver Detalhe**, **Confirmar Entrega** (se SHIPPED), **Cancelar** (só ADMIN).

Botão "Novo Pedido" abre formulário de criação (ver abaixo).

---

### TELA 6 — Novo Pedido / Editar Pedido

**Rota:** `/orders/new` e `/orders/:id/edit`
**Auth:** Todos

Formulário de criação/edição de pedido:

1. **Selecionar Cliente** — campo de busca/autocomplete pelo nome do cliente
2. **Data de Entrega** — date/time picker
3. **Itens do Pedido** — tabela dinâmica onde o usuário pode:
   - Adicionar linha: selecionar produto (autocomplete), quantidade, validade do vasilhame (se água), fornecedor de gás (se gás), preço de custo do gás e flag "recebido por nós"
   - Remover linha
4. **Total calculado** — exibido no rodapé da tabela
5. Botão "Salvar"

O sistema cria automaticamente pedidos separados para água e gás se os itens forem mistos.

---

### TELA 7 — Detalhe do Pedido

**Rota:** `/orders/:id`
**Auth:** Todos

Página com informações completas do pedido:

**Cabeçalho:** ID, Cliente (clicável), Status, Status Pagamento, Data de Criação, Data de Entrega.

**Seção Itens:** tabela com produto, quantidade, preço unitário, subtotal, validade (se água), fornecedor (se gás).

**Seção Resumo Financeiro:** Total do Pedido, Total Pago, Saldo Devedor.

**Seção Pagamentos:** tabela com data, valor, método, observações.

**Ações disponíveis (conforme estado):**
- Status `PENDING` ou `SHIPPED` → botão "Mudar Status" (dropdown com opções)
- Status `SHIPPED` → botão "Confirmar Entrega"
- Status `DELIVERED` e pagamento incompleto → formulário de registro de pagamento (valor, método, observações)
- Pedido não cancelado → botão "Cancelar Pedido" (ADMIN, com confirmação)

---

### TELA 8 — Pedidos Ativos (Entregas)

**Rota:** `/orders/active`
**Auth:** Todos

Lista focada nos pedidos com status `SHIPPED` — o que está "na rua" para entrega. Exibe nome do cliente, endereço, itens resumidos, total, e botão de "Confirmar Entrega" direto na lista para agilizar a operação.

---

### TELA 9 — Estoque

**Rota:** `/stock`
**Auth:** Todos

Tabela de todos os produtos com suas quantidades em estoque. Colunas: Produto, Tipo, Quantidade Atual, Quantidade Mínima, Status (badge: NORMAL verde / LOW STOCK amarelo / OUT OF STOCK vermelho).

Para cada produto, botão de ajuste: abre modal com campo numérico (positivo = entrada, negativo = saída) e botão confirmar.

---

### TELA 10 — Produtos

**Rota:** `/products`
**Auth:** ADMIN para edição, todos para visualização

Tabela de produtos. Colunas: Nome, Tipo, Preço Base, Último Preço de Custo, Fornecedor Padrão (gás), Ativo (badge sim/não).

Ações *(ADMIN)*: **Editar**, **Ativar/Desativar**, **Excluir**.
Botão "Novo Produto" *(ADMIN)* — modal com campos: nome, tipo (WATER / GAS), preço base, preço de custo, fornecedor padrão (só para GAS).

---

### TELA 11 — Vasilhames Emprestados

**Rota:** `/bottles`
**Auth:** Todos

Lista de todos os vasilhames pendentes de devolução. Colunas: Cliente, Produto, Quantidade, Data do Empréstimo, Dias em Aberto. Ordenado por dias em aberto (mais antigo primeiro).

Ações por linha: **Ver Cliente**, **Registrar Devolução** (com confirmação), **Excluir**.

Botão "Registrar Empréstimo" — modal com: cliente (busca), produto (busca), quantidade, data.

---

### TELA 12 — Fornecedores de Gás

**Rota:** `/suppliers`
**Auth:** Todos

Lista paginada de fornecedores. Colunas: Nome, Observações, Ações (editar, excluir).
Botão "Novo Fornecedor" — modal com: nome (obrigatório), observações.

---

### TELA 13 — Liquidações com Fornecedores

**Rota:** `/gas-settlements`
**Auth:** ADMIN para relatório, todos para visualização e liquidação

**Filtros no topo:** Fornecedor (dropdown), Data Início, Data Fim. Botão "Buscar".

**Cards de Resumo:**
- Total que a distribuidora deve ao fornecedor (YOU_OWE)
- Total que o fornecedor deve à distribuidora (SUPPLIER_OWE)
- Saldo líquido

**Tabela:** ID, Fornecedor, Valor, Tipo (YOU_OWE / SUPPLIER_OWE com badge colorido), Liquidado (sim/não), Data de Liquidação.

Ações: **Liquidar Individual**, **Liquidar Tudo** (botão com os filtros ativos).

---

### TELA 14 — Relatórios Financeiros *(ADMIN only)*

**Rota:** `/reports`
**Auth:** ADMIN

Página com abas:

**Aba: Desempenho por Período**
- Campos: Data Início, Data Fim → botão "Gerar"
- Exibe cards: Total Faturado, Total Recebido, Lucro Bruto do Gás, Saldo
- Gráfico de barras: faturamento × recebimentos por período

**Aba: Relatório Diário**
- Campo: Data → botão "Buscar"
- Exibe snapshot do dia (pedidos criados, entregues, valor recebido, etc.)

**Aba: Fluxo de Estoque**
- Campo: Data → botão "Buscar"
- Exibe movimentações de entrada e saída no dia

**Aba: Resumo Operacional**
- Campos: Data Início, Data Fim → botão "Gerar"
- Métricas operacionais do período (pedidos, entregas, clientes atendidos, etc.)

---

### TELA 15 — Gerenciamento de Usuários *(ADMIN only)*

**Rota:** `/users`
**Auth:** ADMIN

Tabela de usuários do sistema. Colunas: Nome Completo, Username, Email, Perfil (badge ADMIN / SELLER), Data de Criação.
Ações: **Editar**, **Excluir** (com confirmação).
Botão "Novo Usuário" — modal com: nome, sobrenome, username, email, senha, perfil.

---

### TELA 16 — Perfil do Usuário Logado

**Rota:** `/profile`
**Auth:** Todos

Página simples com os dados do usuário autenticado. Formulário para alterar nome e senha. Exibe username e email (somente leitura).

---

## Observações Gerais de UX

- **Sidebar fixa** com ícones e labels. Itens exclusivos de ADMIN ocultos para SELLER.
- **Breadcrumb** em todas as páginas internas.
- **Toast notifications** para sucesso/erro em todas as ações (ex: "Pedido criado com sucesso", "Pagamento registrado").
- **Confirmação em modais** antes de excluir ou cancelar qualquer registro.
- **Loading states** visíveis em todas as chamadas de API (spinners, skeleton screens).
- **Paginação** em todas as listas longas (10 itens por página padrão, opção de 25/50).
- **Responsividade:** funcional em tablets (1024px+), otimizado para desktop.
- **Badges coloridos:** PAID = verde, OVERDUE = vermelho, SHIPPED = azul, DELIVERED = verde-escuro, CANCELLED = cinza, PENDING = amarelo.
