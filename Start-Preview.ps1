$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'backend/server.py'
python $scriptPath --port 8765
