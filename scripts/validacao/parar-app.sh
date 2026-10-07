#!/usr/bin/env bash
# Encerra o backend e o proxy iniciados por iniciar-app.sh (PIDs em validacao-out/pids).
# Não mexe no banco: use ambiente-pg.sh down para removê-lo.
set -uo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
RAIZ="$(cd "$AQUI/../.." && pwd)"
PIDS="$RAIZ/validacao-out/pids"

[ -f "$PIDS" ] || { echo "Nenhum serviço registrado (validacao-out/pids não existe)."; exit 0; }

matar() { # nome pid
  local nome="$1" pid="$2"
  if ! kill -0 "$pid" 2>/dev/null; then echo "    $nome (pid $pid) já havia encerrado"; return; fi
  kill "$pid" 2>/dev/null || true
  for _ in 1 2 3 4 5 6 7 8 9 10; do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
  if kill -0 "$pid" 2>/dev/null; then
    kill -9 "$pid" 2>/dev/null || true
    # Git Bash/Windows: o PID do MSYS pode não encerrar o processo nativo; usa o PID do Windows.
    if command -v taskkill >/dev/null 2>&1; then
      winpid="$(ps -p "$pid" 2>/dev/null | awk 'NR==2{print $4}')"
      [ -n "${winpid:-}" ] && MSYS_NO_PATHCONV=1 taskkill /PID "$winpid" /T /F >/dev/null 2>&1 || true
    fi
  fi
  echo "    $nome (pid $pid) encerrado"
}

echo "==> Encerrando serviços"
while IFS='=' read -r nome pid; do
  [ -n "${pid:-}" ] && matar "$nome" "$pid"
done < "$PIDS"
rm -f "$PIDS"
echo "==> Serviços encerrados."
