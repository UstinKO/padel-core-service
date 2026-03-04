#!/bin/bash
# register-players.sh

BASE_URL="http://localhost:8081/api/tournaments"
TOURNAMENT_ID=1  # ID вашего турнира (обычно 1 для первого турнира)

echo "Регистрация игроков на турнир ID: $TOURNAMENT_ID"
echo "========================================"

# Регистрируем игроков с 1 по 20
for i in {1..20}
do
    echo "Регистрация игрока $i..."
    curl -X POST "$BASE_URL/$TOURNAMENT_ID/register?playerId=$i" \
         -H "Content-Type: application/json" \
         -w " -> Status: %{http_code}\n" \
         -s -o /dev/null
    sleep 0.5  # небольшая пауза между запросами
done

echo "========================================"
echo "Регистрация завершена!"