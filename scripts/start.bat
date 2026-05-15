@echo off
setlocal EnableExtensions

rem Repo root = parent of scripts\
pushd "%~dp0.." || (
  echo [start] ERROR: Cannot resolve repository root >&2
  exit /b 1
)
set "REPO_ROOT=%CD%"
popd

set "DB_ONLY=0"
set "DETACH=0"
set "RUN_TESTS=0"
set "RUN_PERF=0"

:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="--db-only" (
  set "DB_ONLY=1"
  shift
  goto parse_args
)
if /i "%~1"=="-d" (
  set "DETACH=1"
  shift
  goto parse_args
)
if /i "%~1"=="--detach" (
  set "DETACH=1"
  shift
  goto parse_args
)
if /i "%~1"=="--test" (
  set "RUN_TESTS=1"
  shift
  goto parse_args
)
if /i "%~1"=="--perf" (
  set "RUN_PERF=1"
  shift
  goto parse_args
)
if /i "%~1"=="-h" goto show_help
if /i "%~1"=="--help" goto show_help
echo [start] ERROR: Unknown argument: %~1 >&2
exit /b 1

:show_help
echo Usage: scripts\start.bat [OPTIONS]
echo.
echo   --db-only   Start only the PostgreSQL container
echo   --test      Start full stack then run integration tests
echo   --perf      Start full stack then run headless performance tests
echo   -d          Run containers in the background (detached)
exit /b 0

:args_done

docker info >nul 2>&1
if errorlevel 1 (
  echo [start] ERROR: Docker is not running. Please start Docker Desktop and try again. >&2
  exit /b 1
)

if "%DB_ONLY%"=="0" (
  echo [start] Building JAR with Gradle...
  pushd "%REPO_ROOT%\api\currencyconverter"
  call gradlew.bat bootJar --quiet
  if errorlevel 1 (
    popd
    exit /b 1
  )
  popd
  echo [start] JAR built: api\currencyconverter\build\libs\
)

set "DETACH_FLAG="
if "%DETACH%"=="1" set "DETACH_FLAG=-d"

if "%DB_ONLY%"=="1" (
  echo [start] Starting database only...
  docker compose -f "%REPO_ROOT%\docker-compose.db.yml" up %DETACH_FLAG%
  exit /b %ERRORLEVEL%
)

if "%RUN_TESTS%"=="1" (
  echo [start] Starting full stack then running integration tests...
  docker compose -f "%REPO_ROOT%\docker-compose.yml" up --build -d
  if errorlevel 1 exit /b 1
  echo [start] Waiting for API to be ready...
  timeout /t 5 /nobreak >nul
  docker compose -f "%REPO_ROOT%\docker-compose.yml" --profile tests run --rm tests python3 -m unittest integration_tests.test_api -v
  exit /b %ERRORLEVEL%
)

if "%RUN_PERF%"=="1" (
  echo [start] Starting full stack then running performance tests...
  docker compose -f "%REPO_ROOT%\docker-compose.yml" up --build -d
  if errorlevel 1 exit /b 1
  echo [start] Waiting for API to be ready...
  timeout /t 5 /nobreak >nul
  docker compose -f "%REPO_ROOT%\docker-compose.yml" --profile tests run --rm tests bash performance_tests/run_perf.sh
  exit /b %ERRORLEVEL%
)

echo [start] Starting full stack (vault + db + api)...
docker compose -f "%REPO_ROOT%\docker-compose.yml" up --build %DETACH_FLAG%
exit /b %ERRORLEVEL%
