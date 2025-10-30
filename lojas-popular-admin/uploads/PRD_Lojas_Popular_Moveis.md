# PRD – Lojas Popular Móveis
### Product Requirements Document (Versão Inicial – BMAD Fullstack)

---

## 1. Introdução e objetivo

O projeto **Lojas Popular Móveis** tem como objetivo construir um **e-commerce completo e acessível** focado na venda de **móveis populares de baixo custo**, privilegiando **desempenho, responsividade e segurança**.

O sistema deve oferecer uma **experiência simples e confiável**, cobrindo todo o ciclo da compra — da navegação pelo catálogo até o pagamento e a administração dos pedidos.

Além disso, a arquitetura precisa ser **flexível e extensível**, permitindo integrações futuras com novos gateways de pagamento, canais de venda e módulos administrativos.

---

## 2. Escopo do projeto

O escopo contempla o desenvolvimento **fullstack** da plataforma, incluindo:
- **Frontend (React + TailwindCSS)** responsivo;
- **Backend (Spring Boot + Spring Security + JPA)** seguro e escalável;
- **Integração com gateway de pagamento (Mercado Pago + extensível)**;
- **Painel administrativo** completo para gestão de produtos e pedidos;
- **Autenticação e autorização com JWT (RBAC)**;
- **Banco de dados relacional (PostgreSQL)** com auditoria;
- **Hospedagem e deploy escalável (Docker + Cloud)**.

---

## 3. Funcionalidades principais

| Módulo | Descrição |
|--------|-----------|
| **Catálogo de Produtos** | Exibição de móveis com nome, preço, descrição, imagem, categoria e disponibilidade. |
| **Carrinho de Compras** | Permite adicionar, remover e atualizar itens antes do checkout. |
| **Checkout** | Página de revisão e confirmação de compra com endereço e forma de pagamento. |
| **Pagamentos** | Integração com Mercado Pago (Pix, Cartão, Boleto) e arquitetura aberta para futuros gateways. |
| **Autenticação** | Login, cadastro, recuperação de senha e controle de sessões via JWT. |
| **Gestão de Usuários** | Perfis de acesso: Administrador, Operador e Cliente. |
| **Painel Administrativo** | Gestão de produtos, pedidos, estoque, banners e promoções. |
| **Auditoria** | Registro de todas as ações críticas (pedidos, pagamentos, login, falhas). |
| **Relatórios** | Visualização de vendas, pagamentos e desempenho por período. |

---

## 4. Módulo de pagamentos

### 4.1 Integração inicial
- Gateway principal: **Mercado Pago (API REST v2)**.
- Métodos de pagamento suportados:
  - Pix (com QR Code dinâmico);
  - Cartão de crédito/débito;
  - Boleto bancário;
  - Carteira Mercado Pago.

### 4.2 Requisitos técnicos
- Comunicação via HTTPS com autenticação Bearer Token;
- Webhooks para atualização assíncrona do status de pagamento;
- Tratamento dos status: `pending`, `approved`, `rejected`, `refunded`, `cancelled`;
- Armazenamento das transações com auditoria (timestamp, método, valor, status);
- Tratamento seguro de exceções e respostas da API.

### 4.3 Extensibilidade
A arquitetura deve permitir integração futura com:
- **PagSeguro**, **Pagar.me**, **Stripe**, **PayPal** e **Zenvia Pay**;
- Padrão de interface de pagamento via `PaymentGatewayStrategy`;
- Configuração via `application.yml` para habilitar novos gateways sem refatorar código.

---

## 5. Módulo de segurança e acessos

### 5.1 Stack de segurança
- **Spring Security 6** com **JWT Tokens** (access e refresh);
- **BCrypt** para hash de senhas;
- Filtro customizado `OncePerRequestFilter` para validação de tokens;
- Controle de CORS e CSRF.

### 5.2 Perfis de acesso (RBAC)
| Perfil | Permissões |
|--------|------------|
| **Administrador (`ROLE_ADMIN`)** | Gestão completa de usuários, produtos, pedidos e pagamentos. |
| **Operador (`ROLE_OPERADOR`)** | Gestão de pedidos e estoque. |
| **Cliente (`ROLE_CLIENTE`)** | Compra, histórico e atualização de conta. |

### 5.3 Funcionalidades de segurança
- Login e logout seguros via JWT;
- Expiração e renovação automática de tokens;
- Recuperação de senha via token temporário;
- Logs e auditoria de autenticação;
- Proteção contra:
  - Injeção SQL;
  - XSS e CSRF;
  - Session fixation;
  - Brute-force (limite de tentativas de login);
  - Acesso indevido a endpoints por papéis.

---

## 6. Requisitos técnicos

### 6.1 Backend
- Linguagem: **Java 21**
- Framework: **Spring Boot 3.5+**
- Módulos: Web, Security, Data JPA, Validation, Mail, Actuator
- Banco de dados: **PostgreSQL**
- Integrações:
  - Mercado Pago API (REST)
  - Amazon S3 (upload de imagens)
- Build: **Maven**
- Deploy: **Docker + Cloud (AWS ou Render)**

### 6.2 Frontend
- Framework: **React (Vite ou Next.js)**
- Estilo: **TailwindCSS**
- Estado: **Redux Toolkit ou Context API**
- Rotas: React Router com proteção por JWT
- Testes: Jest + React Testing Library

---

## 7. Requisitos não funcionais

| Categoria | Descrição |
|-----------|-----------|
| **Performance** | Resposta média < 1s; carregamento inicial < 2s. |
| **Escalabilidade** | Suporte a até 10.000 acessos simultâneos. |
| **Segurança** | Conformidade com **LGPD** e **PCI DSS**. |
| **Disponibilidade** | 99,5% de uptime mensal. |
| **Logs e Monitoramento** | Centralizados via Spring Actuator e ELK Stack. |
| **Backup** | Diários no banco e arquivos no S3. |

---

## 8. Critérios de sucesso

1. Plataforma acessível, responsiva e segura.  
2. Pagamentos via **Mercado Pago** funcionando em produção.  
3. Login, cadastro e painel administrativo operacionais.  
4. Auditoria completa de pedidos, pagamentos e logins.  
5. Possibilidade de habilitar novos gateways de pagamento sem refatoração.  
6. Todos os fluxos testados (unitário, integração e e2e).  

---

## 9. Cronograma e entregas (macro)

| Fase | Entregável | Duração estimada |
|------|------------|-----------------|
| 1 | Definição e PRD (este documento) | Concluído |
| 2 | Arquitetura técnica e setup do projeto | 1 semana |
| 3 | Desenvolvimento do backend (API + Auth + Pagamentos) | 3 semanas |
| 4 | Desenvolvimento do frontend (UI + Checkout + Painel) | 3 semanas |
| 5 | Testes integrados e ajustes finais | 1 semana |
| 6 | Deploy e homologação final | 1 semana |

---

## 10. Próximos passos (Fluxo BMAD)

1. Execute o comando:
   ```text
   *agent architect
   ```
   para gerar a **arquitetura técnica completa** com base neste PRD.
2. Em seguida:
   ```text
   *workflow-guidance
   ```
   para iniciar o **fluxo automatizado de desenvolvimento fullstack**.
3. Após aprovação do PRD e da arquitetura, o agente `dev` inicia o **primeiro módulo: Login + Autenticação JWT**.

---

**Documento gerado conforme BMAD-METHOD™ – Team Fullstack Configuration**  
© 2025 – Projeto: *Lojas Popular Móveis*  
Autor: **Joaquim Moura**
