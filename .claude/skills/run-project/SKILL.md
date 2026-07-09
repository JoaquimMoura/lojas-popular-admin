---
name: run-project
description: Sobe o backend (Spring Boot) e o frontend (Vite/React) deste projeto localmente. Use quando o usuário pedir para "subir o projeto", "rodar o front e back" ou "iniciar a aplicação".
---

# Rodando Lojas Popular (backend + frontend) localmente

## Pré-requisitos
- JDK 21 instalado. Neste ambiente ele fica em `C:\Program Files\Java\jdk-21`
  (o `JAVA_HOME` padrão do sistema aponta para o JDK 17, que **não compila/roda**
  este projeto — o pom.xml exige `<java.version>21</java.version>`).
- Node/npm já com `node_modules` instalado em `lojas-popular-admin/` (rodar
  `npm install` lá se a pasta não existir).
- Não é necessário Docker/Postgres para rodar local: o perfil `local` usa um
  banco H2 em arquivo (`./data/lojasdb`).

## 1. Backend (Spring Boot, porta 8080)

Na raiz do projeto, forçar o JDK 21 e usar o profile `local`:

```bash
JAVA_HOME="C:\Program Files\Java\jdk-21" PATH="/c/Program Files/Java/jdk-21/bin:$PATH" \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Startup ok quando aparecer no log: `Started LojasPopularBackendApplication in N seconds`.

Erros de RabbitMQ ("Failed to check/redeclare auto-delete queue(s)") são
esperados/inofensivos quando não há RabbitMQ rodando localmente — a
aplicação sobe normalmente mesmo assim.

Smoke test:
```bash
curl -i http://localhost:8080/api/v1/
```
Um `401` é esperado (endpoint protegido) e confirma que o servidor está no ar.

## 2. Frontend (Vite/React, porta 5173)

```bash
cd lojas-popular-admin
npm run dev
```

Sobe em `http://localhost:5173/`. O CORS do backend (perfil `local`) já libera
as portas 5173-5177 e 3000, e o frontend já está configurado para chamar a API
em `http://localhost:8080/api/v1` (ver `lojas-popular-admin/.env` e
`src/services/api.js`).

## Notas
- Ambos os comandos são de longa duração — rode cada um em background (ex.:
  `run_in_background` no Claude Code, ou terminais separados).
- Se o backend falhar com `UnsupportedClassVersionError` / "class file
  version 65.0", é o mesmo problema de JDK 17 vs 21 — reforçar o `JAVA_HOME`
  do JDK 21 como no comando acima.
