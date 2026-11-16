[CmdletBinding()]
param(
    [string]$StarsectorPath = $env:STARSECTOR_HOME,
    [string]$JdkPath = $env:JAVA_HOME,
    [string]$KotlinPath = $env:KOTLIN_HOME,
    [string]$KotlinRuntimeReference = $env:KOTLIN_STDLIB,
    [string]$ConsoleJarPath,
    [switch]$Test,
    [switch]$Package
)
$ErrorActionPreference = 'Stop'
if (-not $StarsectorPath) { $StarsectorPath = Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'Starsector' }
$tools = Join-Path (Split-Path $PSScriptRoot -Parent) 'tools'
if (-not $JdkPath) { $JdkPath = (Get-ChildItem $tools -Directory -Filter 'jdk-17*' | Sort-Object Name -Descending | Select-Object -First 1).FullName }
if (-not $KotlinPath) { $KotlinPath = Join-Path ((Get-ChildItem $tools -Directory -Filter 'kotlin-*' | Sort-Object Name -Descending | Select-Object -First 1).FullName) 'kotlinc' }
$info = Get-Content (Join-Path $PSScriptRoot 'mod_info.json') -Raw | ConvertFrom-Json
$tracking = Get-Content (Join-Path $PSScriptRoot 'ScanThoseGates.version') -Raw | ConvertFrom-Json
$version = "$($info.version.major).$($info.version.minor).$($info.version.patch)"
if ($version -ne "$($tracking.modVersion.major).$($tracking.modVersion.minor).$($tracking.modVersion.patch)") { throw 'Version Checker metadata differs from mod version.' }
if ((Get-Content (Join-Path $PSScriptRoot 'changelog.txt') -First 1) -ne "Version $version") { throw 'Changelog header differs from mod version.' }
$registered = @(Get-Content (Join-Path $PSScriptRoot 'data\config\version\version_files.csv') | Select-Object -Skip 1 | Where-Object { $_.Trim() })
if ($registered.Count -ne 1 -or $registered[0].Trim() -ne 'ScanThoseGates.version') { throw 'Version Checker registration is incorrect.' }
$java = Join-Path $JdkPath 'bin\java.exe'
$javac = Join-Path $JdkPath 'bin\javac.exe'
$jar = Join-Path $JdkPath 'bin\jar.exe'
$compiler = Join-Path $KotlinPath 'lib\kotlin-compiler.jar'
foreach ($tool in @($java, $javac, $jar, $compiler)) { if (-not (Test-Path -LiteralPath $tool)) { throw "Missing tool: $tool" } }
$core = Join-Path $StarsectorPath 'starsector-core'
$mods = Get-ChildItem (Join-Path $StarsectorPath 'mods') -Directory
$dependencies = @((Get-ChildItem $core -Filter '*.jar').FullName)
foreach ($modId in @('lw_lazylib', 'lunalib')) {
    $folders = @($mods | Where-Object { $p = Join-Path $_.FullName 'mod_info.json'; (Test-Path $p) -and ([regex]::Match((Get-Content $p -Raw), '"id"\s*:\s*"([^"]+)"').Groups[1].Value -eq $modId) })
    if ($folders.Count -ne 1) { throw "Expected one $modId dependency for compilation." }
    $dependencies += (Get-ChildItem $folders[0].FullName -Recurse -Filter '*.jar').FullName
}
if (-not $ConsoleJarPath) {
    $console = @($mods | Where-Object { $p = Join-Path $_.FullName 'mod_info.json'; (Test-Path $p) -and ([regex]::Match((Get-Content $p -Raw), '"id"\s*:\s*"([^"]+)"').Groups[1].Value -eq 'lw_console') })
    $candidates = @($console | ForEach-Object { (Get-ChildItem $_.FullName -Recurse -Filter 'lw_Console.jar').FullName })
    $ConsoleJarPath = $candidates | Where-Object { (Test-Path $_) -and (Get-Item $_).Length -gt 4 } | Select-Object -First 1
}
if (-not $ConsoleJarPath -or -not (Test-Path $ConsoleJarPath)) { throw 'A valid Console Commands JAR is needed to compile the optional uninstall command; pass -ConsoleJarPath.' }
$dependencies += $ConsoleJarPath
$classpath = $dependencies -join ';'
if (-not $KotlinRuntimeReference) {
    $reference = Join-Path $tools 'reference-libs\Kotlin-Runtime-2.1.20.jar'
    $KotlinRuntimeReference = if (Test-Path $reference) { $reference } else { Join-Path $KotlinPath 'lib\kotlin-stdlib.jar' }
}
# Compile against a known API-level reference, but run checks against the installed runtime.
# A newer LazyLib may ship Kotlin metadata newer than the locally installed compiler.
$compileDependencies = @($dependencies | Where-Object { $_ -notmatch 'Kotlin-Runtime|LazyLib-Kotlin' }) + $KotlinRuntimeReference
$compileClasspath = $compileDependencies -join ';'
$classes = Join-Path $PSScriptRoot ('build\classes-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$allSources = @((Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -File | Where-Object Extension -In '.java', '.kt').FullName)
# Kotlin resolves Java source declarations; javac then emits their classes into the same output directory.
& $java -cp (Join-Path $KotlinPath 'lib\*') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -jvm-target 17 -language-version 2.1 -api-version 2.1 -no-stdlib -no-reflect -classpath $compileClasspath -d $classes $allSources
if ($LASTEXITCODE -ne 0) { throw 'Kotlin compilation failed.' }
& $javac --release 17 -encoding UTF-8 -cp "$classes;$classpath" -d $classes @((Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java').FullName)
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
if ($Test) {
    $testClasses = Join-Path $classes 'test-classes'
    New-Item -ItemType Directory -Path $testClasses -Force | Out-Null
    & $javac --release 17 -encoding UTF-8 -cp "$classes;$classpath" -d $testClasses @((Get-ChildItem (Join-Path $PSScriptRoot 'tests') -Recurse -Filter '*.java').FullName)
    if ($LASTEXITCODE -ne 0) { throw 'Regression test compilation failed.' }
    & $java '-Djava.awt.headless=true' -cp "$testClasses;$classes;$classpath" org.aero.scanThoseGates.ScannerRegressionTest
    if ($LASTEXITCODE -ne 0) { throw 'Regression tests failed.' }
    # Optional integrations must not be required to load/run the scanner classes.
    $minimal = @($dependencies | Where-Object { $_ -notmatch 'LunaLib|fuzzywuzzy|lw_Console' }) -join ';'
    & $java '-Djava.awt.headless=true' -cp "$testClasses;$classes;$minimal" org.aero.scanThoseGates.ScannerRegressionTest
    if ($LASTEXITCODE -ne 0) { throw 'Regression tests without optional integrations failed.' }
}
$builtJar = Join-Path $classes 'ScanThoseGates.jar'
& $jar "-J-Djava.io.tmpdir=$classes" --create --file $builtJar -C $classes org -C $classes META-INF
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
Copy-Item -LiteralPath $builtJar -Destination (Join-Path $PSScriptRoot 'jars\ScanThoseGates.jar') -Force
if ($Package) {
    $mod = Join-Path $PSScriptRoot ('dist\stage-' + [guid]::NewGuid().ToString('N') + '\ScanThoseGates')
    New-Item -ItemType Directory -Path (Join-Path $mod 'jars') -Force | Out-Null
    foreach ($item in @('data', 'graphics', 'mod_info.json', 'ScanThoseGates.version', 'README.md', 'changelog.txt', 'LICENSE')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $item) -Destination $mod -Recurse }
    Copy-Item -LiteralPath $builtJar -Destination (Join-Path $mod 'jars\ScanThoseGates.jar')
    $info = Get-Content (Join-Path $PSScriptRoot 'mod_info.json') -Raw | ConvertFrom-Json
    $version = "$($info.version.major).$($info.version.minor).$($info.version.patch)"
    $zip = Join-Path $PSScriptRoot 'dist\ScanThoseGates.zip'
    Compress-Archive -Path $mod -DestinationPath $zip -Force
    $hash = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $([IO.Path]::GetFileName($zip))" | Set-Content ($zip + '.sha256') -Encoding ascii
    Write-Host "Package: $zip"
}
Write-Host 'Built Java 17/Kotlin 2.1 compatible mod; Kotlin runtime is supplied by LazyLib.'
