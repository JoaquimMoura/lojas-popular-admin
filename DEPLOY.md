# Deploy — Lojas Popular

Guia de referência pra fazer alterações e subir pra produção sem se perder.
Produção: `https://popularmoveis.app.br`, rodando na VPS Hostinger (`srv1817871.hstgr.cloud`,
IP `179.197.67.211`), atrás do Traefik já existente (rede `host`, certresolver `letsencrypt`).

## 1. Visão geral (o que existe e onde)

- **Repositório**: `https://github.com/JoaquimMoura/lojas-popular-admin` — branch **`master`**
  é a que importa (o GitHub mostra `main` como padrão, mas está desatualizado; sempre use
  `master`).
- **Na VPS**: código clonado em `/docker/lojas-popular`.
- **3 containers** (`docker-compose.prod.yml`): `lojas-postgres-prod`, `lojas-backend-prod`,
  `lojas-frontend-prod`.
- **Segredos reais**: arquivo `/docker/lojas-popular/.env` na VPS (nunca commitado no git).
- **Uploads de imagem**: ficam em `/docker/lojas-popular-uploads` na própria VPS (bind mount,
  fora de `/docker/lojas-popular` onde está o código) — sobrevive a rebuilds e dá pra navegar
  como pasta normal. Dentro dela, as imagens de produto ficam organizadas por categoria:
  `produtos/<slug-da-categoria>/` (ex.: `produtos/dormitorio/`, `produtos/cozinha/`), com uma
  subpasta `galeria/` em cada uma. O slug é gerado a partir do nome da categoria cadastrada no
  banco (sem acento, minúsculo, espaços trocados por `-`), então toda categoria nova já ganha
  pasta própria automaticamente no primeiro upload — não precisa criar nada manualmente, mas
  o passo abaixo cria as 6 pastas atuais de antemão.
- **Banco**: Postgres real dentro do container `lojas-postgres-prod`, schema criado por
  migrations Flyway (`src/main/resources/db/migration/`).

## 1.1 Passo único na VPS: preparar a pasta de uploads (antes do próximo deploy)

Essa mudança troca o volume Docker nomeado `uploads_data` por uma pasta real em
`/docker/lojas-popular-uploads`. É preciso criar essa pasta **uma vez** antes de rodar
`docker compose up -d --build` com o código novo, senão o Docker cria uma pasta vazia
dona de `root` e o backend (que roda como usuário não-root `app` dentro do container) não
vai conseguir gravar nela.

```bash
# 1) Cria a pasta base e as 6 subpastas de categoria já conhecidas hoje
mkdir -p /docker/lojas-popular-uploads/produtos/{dormitorio,cozinha,sala,escritorio,lavanderia,banheiro}
mkdir -p /docker/lojas-popular-uploads/produtos/{dormitorio,cozinha,sala,escritorio,lavanderia,banheiro}/galeria
mkdir -p /docker/lojas-popular-uploads/categorias

# 2) Se já existiam uploads no volume antigo, copie o conteúdo pra pasta nova ANTES do
#    próximo "up --build" (senão as fotos de produtos já cadastrados somem visualmente,
#    mesmo que a URL continue no banco):
docker run --rm -v uploads_data:/old -v /docker/lojas-popular-uploads:/new alpine \
  sh -c "cp -a /old/. /new/ 2>/dev/null || true"

# 3) Ajusta o dono da pasta pro mesmo usuário que o container backend usa (evita erro de
#    permissão no upload). Descubra o UID/GID reais com:
docker exec lojas-backend-prod id app
# Exemplo de saída: uid=100(app) gid=101(app) — troque os números abaixo pelos que aparecerem:
chown -R 100:101 /docker/lojas-popular-uploads
```

Depois disso, siga o fluxo normal da seção 2. Esse passo só precisa ser feito uma vez.

## 2. Fluxo padrão pra qualquer alteração de código

Isso vale tanto pra um ajuste no backend (`src/main/java/...`) quanto no frontend
(`lojas-popular-admin/src/...`).

### No seu computador (aqui com o Claude Code):

1. Peça a alteração normalmente (ex.: "corrige X", "adiciona campo Y").
2. Quando terminar, peça pra eu **commitar e enviar pro GitHub** (`git push origin master`)
   — eu só faço isso quando você pede.

### Na VPS (Terminal, botão no painel Hostinger):

```bash
cd /docker/lojas-popular
git checkout master          # garante que está na branch certa
git pull origin master       # baixa as alterações novas
docker compose -f docker-compose.prod.yml up -d --build
```

O `up -d --build` reconstrói só o que mudou (Docker usa cache de camadas) e recria os
containers necessários, sem apagar o banco nem os uploads (eles ficam em volumes separados).

**Se só mudou um arquivo `.sql` de migration ou configuração**, o mesmo comando funciona —
não precisa de passo diferente.

## 3. Comandos do dia a dia

Sempre a partir de `/docker/lojas-popular`:

```bash
# Ver se os 3 containers estão de pé
docker compose -f docker-compose.prod.yml ps

# Ver logs do backend (últimas 50 linhas, sem ficar "pendurado" acompanhando)
docker compose -f docker-compose.prod.yml logs backend --tail=50

# Acompanhar logs em tempo real (Ctrl+C pra sair — não deixe rodando muito tempo,
# o terminal do navegador pode cair)
docker compose -f docker-compose.prod.yml logs backend -f

# Reiniciar um container sem rebuildar (útil se só quer "dar um restart")
docker compose -f docker-compose.prod.yml restart backend

# Parar tudo
docker compose -f docker-compose.prod.yml down

# Subir tudo de novo (sem rebuild, se as imagens não mudaram)
docker compose -f docker-compose.prod.yml up -d
```

## 4. Acessar o banco de produção direto

```bash
docker exec -it lojas-postgres-prod psql -U lojas_popular -d lojas_popular
```

Dentro do `psql`: `\dt` lista tabelas, `SELECT * FROM produtos LIMIT 5;` consulta,
`\q` sai.

## 5. Se algo der errado depois de um deploy

```bash
# Ver se o backend realmente terminou de iniciar (procure por "Started LojasPopularBackendApplication")
docker compose -f docker-compose.prod.yml logs backend --tail=100

# Voltar pro commit anterior (rollback rápido)
git log --oneline -5          # ache o hash do commit anterior que funcionava
git checkout <hash-do-commit-anterior>
docker compose -f docker-compose.prod.yml up -d --build
git checkout master           # depois de resolvido, volte pra master
```

## 6. RabbitMQ desligado (esperado)

O RabbitMQ ainda não está de pé (notificação WhatsApp fica inativa). O listener
do consumidor está com `auto-startup: false` (`application.yml`), então o
backend não tenta se conectar e não fica logando erro de conexão. Isso só vira
problema de verdade se aparecer `APPLICATION FAILED TO START` ou o container
ficar em `Restarting`.

## 7. Segurança / pendências conhecidas

- [ ] Trocar a senha do admin (`admin@popularmoveis.app.br`) — hoje não tem tela de
      "alterar senha" no painel; peça pra gerar um novo hash e atualizar direto no banco.
- [ ] Configurar backup automático do Postgres de produção (hoje só existe o volume Docker,
      sem rotina de backup).

## 8. Onde estão as coisas no `.env` (VPS, nunca no git)

Arquivo: `/docker/lojas-popular/.env`. Baseado em `.env.prod.example` do repositório.
Contém: senha do Postgres, `JWT_SECRET`, credenciais de RabbitMQ/WhatsApp/Mercado Pago.
Se precisar trocar algo, edite com `nano .env`, salve (`Ctrl+O`, Enter, `Ctrl+X`) e rode
`docker compose -f docker-compose.prod.yml up -d` (não precisa `--build` pra mudança só de
variável de ambiente, mas precisa recriar o container — `up -d` já faz isso quando percebe
que a config mudou).
