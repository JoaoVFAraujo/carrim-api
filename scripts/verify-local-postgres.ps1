param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$JavaHome = 'C:\Program Files\Java\jdk-25.0.4.1',
    [string[]]$MavenGoals = @('verify')
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$testRoot = Join-Path $projectRoot 'target\postgres-tests'
$cluster = Join-Path $testRoot ([Guid]::NewGuid().ToString())
$null = New-Item -ItemType Directory -Path $testRoot -Force
foreach ($binary in @('initdb.exe', 'pg_ctl.exe', 'createdb.exe')) {
    if (-not (Test-Path (Join-Path $PostgresBin $binary))) { throw "PostgreSQL binary missing: $binary" }
}
if (-not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) { throw 'JDK missing' }
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
$testPort = $listener.LocalEndpoint.Port
$listener.Stop()
$previous = @{}
foreach ($key in @('JAVA_HOME', 'PATH', 'MAVEN_USER_HOME', 'CARRIM_TEST_DATABASE_URL')) {
    $previous[$key] = [Environment]::GetEnvironmentVariable($key)
}
$running = $false
Push-Location $projectRoot
try {
    $init = Start-Process -FilePath (Join-Path $PostgresBin 'initdb.exe') -ArgumentList @(
        '-D', "`"$cluster`"", '--username=carrim_test', '--auth=trust', '--encoding=UTF8', '--locale=C'
    ) -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput "$cluster-init.log" -RedirectStandardError "$cluster-init-error.log"
    if ($init.ExitCode -ne 0) { throw "initdb failed; see $cluster-init-error.log" }
    $start = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList @(
        'start', '-D', "`"$cluster`"", '-l', "`"$cluster.log`"", '-o', "`"-h 127.0.0.1 -p $testPort -F`"", '-w'
    ) -WindowStyle Hidden -PassThru
    $start.WaitForExit()
    if ($start.ExitCode -ne 0) { throw 'Isolated PostgreSQL failed to start' }
    $running = $true
    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $testPort -U carrim_test carrim_test
    if ($LASTEXITCODE -ne 0) { throw 'Test database creation failed' }
    $env:JAVA_HOME = $JavaHome
    $env:PATH = "$JavaHome\bin;$env:PATH"
    $env:MAVEN_USER_HOME = Join-Path $projectRoot 'target\maven-user-home'
    $env:CARRIM_TEST_DATABASE_URL = "jdbc:postgresql://127.0.0.1:$testPort/carrim_test"
    & .\mvnw.cmd -B -ntp "-Dmaven.repo.local=$projectRoot\target\maven-repository" @MavenGoals
    if ($LASTEXITCODE -ne 0) { throw 'Maven validation failed' }
} finally {
    if ($running) {
        $stop = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList @(
            'stop', '-D', "`"$cluster`"", '-m', 'fast', '-w'
        ) -WindowStyle Hidden -Wait -PassThru
        if ($stop.ExitCode -ne 0) { Write-Warning "Isolated test server did not stop; cluster: $cluster" }
    }
    foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key]) }
    Pop-Location
}
