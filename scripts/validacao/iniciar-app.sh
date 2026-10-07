#!/usr/bin/env bash
# Sobe a aplicação LOCAL para a validação: backend (JAR), build do frontend e proxy que emula o Traefik.
# Pré-requisito: scripts/validacao/ambiente-pg.sh up. Usa SOMENTE o banco descartável lp-val-pg.
#
# Variáveis opcionais: LP_BACKEND_PORT (18090), LP_PROXY_PORT (4180), LP_UPLOAD_DIR,
#   LP_JAVA_HOME (JDK 21, se o java do PATH não for 21+), LP_PULAR_BUILD=1 (reaproveita validacao-out/app.jar),
#   LP_SEM_FRONT=1 (não compila o frontend; o proxy serve só a API).
# Saída: validacao-out/{app.jar,backend.log,proxy.log,front-dist,pids}.
set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
RAIZ="$(cd "$AQUI/../.." && pwd)"
OUT="$RAIZ/validacao-out"
ENV_FILE="$AQUI/.ambiente.env"
FRONT="$RAIZ/lojas-popular-admin"

nativo() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }

[ -f "$ENV_FILE" ] || { echo "Arquivo $ENV_FILE não encontrado: rode ambiente-pg.sh up antes."; exit 2; }
# Carrega o .ambiente.env sem sobrescrever variáveis já definidas no ambiente da sessão.
while IFS='=' read -r chave valor; do
  case "$chave" in ''|'#'*) continue ;; esac
  if [ -z "${!chave:-}" ]; then export "$chave=$valor"; fi
done < "$ENV_FILE"

BACK_PORT="${LP_BACKEND_PORT:-18090}"
PROXY_PORT="${LP_PROXY_PORT:-4180}"
mkdir -p "$OUT"

if [ -s "$OUT/pids" ]; then
  echo "Há serviços registrados em validacao-out/pids. Rode parar-app.sh antes de iniciar de novo."; exit 2
fi
docker ps --format '{{.Names}}' | grep -qx 'lp-val-pg' || { echo "O container lp-val-pg não está rodando (ambiente-pg.sh up)."; exit 2; }

# ---- Java 21+ (o mvnw usa JAVA_HOME: ele é sempre derivado do java escolhido)
if [ -n "${LP_JAVA_HOME:-}" ]; then
  if command -v cygpath >/dev/null 2>&1; then LP_JAVA_HOME="$(cygpath -u "$LP_JAVA_HOME")"; fi
  PATH="$LP_JAVA_HOME/bin:$PATH"
fi
JAVA_MAJOR="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\)[."].*/\1/p')"
if [ -z "$JAVA_MAJOR" ] || [ "$JAVA_MAJOR" -lt 21 ]; then
  echo "Java 21+ é necessário (encontrado: ${JAVA_MAJOR:-nenhum}). Defina LP_JAVA_HOME com o diretório de um JDK 21."; exit 2
fi
JAVA_BIN="$(command -v java)"
JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
export JAVA_HOME="$(nativo "$JAVA_HOME")"

# ---- Backend: compila uma vez e roda uma CÓPIA do JAR (não trava o target/)
if [ "${LP_PULAR_BUILD:-}" = "1" ] && [ -f "$OUT/app.jar" ]; then
  echo "==> Reaproveitando $OUT/app.jar (LP_PULAR_BUILD=1)"
else
  echo "==> Compilando o backend (uma vez)"
  ( cd "$RAIZ"
    ./mvnw -o -q -DskipTests package || { echo "    Falhou offline; tentando com rede..."; ./mvnw -q -DskipTests package; } )
  JAR="$(ls -t "$RAIZ"/target/*.jar | grep -v '\.original$' | head -1)"
  [ -n "$JAR" ] || { echo "JAR não encontrado em target/."; exit 1; }
  cp "$JAR" "$OUT/app.jar"
fi

echo "==> Iniciando o backend na porta $BACK_PORT"
export PROD_DB_URL="jdbc:postgresql://localhost:${LP_PG_PORT}/${LP_PG_DB}"
export PROD_DB_USER="$LP_PG_USER"
export PROD_DB_PASSWORD="$LP_PG_PASSWORD"
export JWT_SECRET="$LP_JWT_SECRET"
# Valores FICTÍCIOS: RabbitMQ aponta para uma porta sem serviço e as chaves de WhatsApp/Mercado Pago não são reais.
export RABBITMQ_HOST=127.0.0.1 RABBITMQ_PORT=5999 RABBITMQ_USER=fake RABBITMQ_PASSWORD=fake
export WHATSAPP_API_KEY=fake-whatsapp-key MP_ACCESS_TOKEN=TEST-fake-token MP_WEBHOOK_SECRET=fake-webhook-secret
export PROD_SERVER_PORT="$BACK_PORT"
export UPLOAD_DIR="$LP_UPLOAD_DIR"
export CORS_ORIGINS="http://localhost:$PROXY_PORT"
mkdir -p "$LP_UPLOAD_DIR"
nohup java -jar "$(nativo "$OUT/app.jar")" > "$OUT/backend.log" 2>&1 &
PID_BACK=$!
echo "backend=$PID_BACK" > "$OUT/pids"

# ---- Frontend (build estático servido pelo proxy)
DIST="$OUT/front-dist"
if [ "${LP_SEM_FRONT:-}" = "1" ]; then
  echo "==> Frontend ignorado (LP_SEM_FRONT=1)"
  mkdir -p "$DIST"
else
  echo "==> Compilando o frontend (VITE_API_BASE_URL=/api/v1)"
  if [ ! -d "$FRONT/node_modules" ]; then ( cd "$FRONT" && npm ci ); fi
  # MSYS_NO_PATHCONV: no Git Bash, "/api/v1" viraria "C:/Program Files/Git/api/v1" e o frontend não acharia a API.
  if ( cd "$FRONT" && MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' VITE_API_BASE_URL=/api/v1 npm run build -- --outDir "$(nativo "$DIST")" --emptyOutDir ) > "$OUT/frontend-build.log" 2>&1; then
    if grep -rq 'Program Files/Git/api' "$DIST/assets" 2>/dev/null; then
      echo "    ERRO: o build gravou um caminho do Git Bash no lugar de /api/v1 (conversão de caminhos); abortando."; exit 1
    fi
    echo "    build concluído"
  else
    echo "    AVISO: o build do frontend falhou (veja validacao-out/frontend-build.log). A API continua disponível."
    mkdir -p "$DIST"
  fi
fi

echo "==> Iniciando o proxy na porta $PROXY_PORT"
LP_FRONT_DIST="$(nativo "$DIST")" LP_BACKEND_PORT="$BACK_PORT" LP_PROXY_PORT="$PROXY_PORT" \
  nohup node "$(nativo "$AQUI/servidor_proxy.js")" > "$OUT/proxy.log" 2>&1 &
echo "proxy=$!" >> "$OUT/pids"

echo "==> Aguardando os serviços (até 180 s)"
ok=0
for _ in $(seq 1 90); do
  if curl -fs -o /dev/null "http://localhost:$PROXY_PORT/api/v1/produtos"; then ok=1; break; fi
  if ! kill -0 "$PID_BACK" 2>/dev/null; then echo "O backend encerrou. Últimas linhas do log:"; tail -n 30 "$OUT/backend.log"; "$AQUI/parar-app.sh" || true; exit 1; fi
  sleep 2
done
if [ "$ok" != 1 ]; then echo "Os serviços não ficaram prontos a tempo. Veja validacao-out/backend.log"; tail -n 30 "$OUT/backend.log"; exit 1; fi

echo "==> Pronto: http://localhost:$PROXY_PORT (API em /api/v1, backend direto em :$BACK_PORT)"
echo "    Próximo passo: export LP_ADMIN_EMAIL/LP_ADMIN_PASSWORD e rode python3 scripts/validacao/api_etapa1.py"
