@echo off
REM Script to clear all Redis data
REM Usage: clear-redis.bat

echo Clearing all Redis data...
docker exec redis redis-cli FLUSHDB
echo.
echo Redis database cleared!
echo.
echo Note: If trading bot is running, new idempotency keys will be created automatically.
echo To stop new keys from being created, stop the trading bot first.
