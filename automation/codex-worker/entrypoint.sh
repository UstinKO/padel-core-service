#!/bin/sh
set -eu
# gh использует GH_TOKEN из приватного env-файла; значение не выводим в журнал.
gh auth setup-git
exec python3 relay.py
