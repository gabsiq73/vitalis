param([Parameter(Mandatory)][string]$Destination)
$ErrorActionPreference = 'Stop'
if (!$env:JAVA_HOME) { throw 'Set JAVA_HOME to a Windows x64 JDK 21.' }
$jdk = Join-Path $env:JAVA_HOME bin
$root = Split-Path $PSScriptRoot
Push-Location $root
try {
    & .\mvnw.cmd -DskipTests package -q
    if ($LASTEXITCODE) { throw 'Maven package failed.' }
    $jar = @(Get-ChildItem target -Filter '*.jar')
    if ($jar.Count -ne 1) { throw 'Expected one executable JAR in target.' }
    $scratch = Join-Path $env:TEMP ([guid]::NewGuid().ToString())
    New-Item -ItemType Directory $scratch | Out-Null
    try {
        Push-Location $scratch
        try { & "$jdk/jar.exe" xf $jar[0].FullName }
        finally { Pop-Location }
        if ($LASTEXITCODE) { throw 'JAR extraction failed.' }
        $modules = & "$jdk/jdeps.exe" --ignore-missing-deps --multi-release 21 --print-module-deps --class-path "$scratch/BOOT-INF/lib/*" "$scratch/BOOT-INF/classes" "$scratch/BOOT-INF/lib"
        if ($LASTEXITCODE) { throw 'jdeps failed.' }
        $dest = New-Item -ItemType Directory -Force $Destination
        # Service providers and modules loaded reflectively by JDBC, Spring and Tomcat.
        $modules = "$modules,jdk.crypto.ec,jdk.unsupported,jdk.zipfs,java.instrument,java.management,java.naming,java.sql"
        & "$jdk/jlink.exe" --add-modules $modules --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output (Join-Path $dest.FullName jre)
        if ($LASTEXITCODE) { throw 'jlink failed. Use a fresh destination.' }
        Copy-Item $jar[0].FullName (Join-Path $dest.FullName 'vitalis-api.jar')
    } finally {
        if ((Resolve-Path $scratch).Path -eq $scratch -and (Split-Path $scratch) -eq $env:TEMP) {
            Remove-Item -LiteralPath $scratch -Recurse -Force
        }
    }
} finally { Pop-Location }
