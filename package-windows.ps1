<#
EditorX Windows 打包脚本（PowerShell）

目标：
- 生成可运行的 Windows 产物（app-image 或 exe 安装包）
- 将内置插件 JAR 放入 plugins/ 目录，保证运行时通过 JarPluginLoader 加载

用法示例：
  pwsh -File package-windows.ps1 -Type app-image -Version 1.0.0
  pwsh -File package-windows.ps1 -Type exe -Version 1.0.0

说明：
- 生成 exe 安装包通常需要安装 WiX Toolset（jpackage 在 Windows 上的依赖）
- 本脚本默认使用 gradlew.bat，需在 Windows 环境运行
#>

[CmdletBinding()]
param(
  [ValidateSet("app-image", "exe")]
  [string]$Type = "app-image",

  [string]$Version = "dev"
)

$ErrorActionPreference = "Stop"

$Root = $PSScriptRoot
Set-Location $Root

function Info([string]$msg) { Write-Host "[INFO] $msg" -ForegroundColor Green }
function Warn([string]$msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Fail([string]$msg) { throw $msg }

Info "Repo 根目录: $Root"

if (-not (Test-Path "$Root\\gradlew.bat")) {
  Fail "未找到 gradlew.bat：$Root\\gradlew.bat"
}

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
  Fail "未找到 jpackage，请安装包含 jpackage 的 JDK（建议 JDK 21）并加入 PATH"
}

if ($Type -eq "exe") {
  Warn "你选择了 --type exe：若报错，请先安装 WiX Toolset（Windows 上 jpackage 常见依赖）"
}

Info "清理并构建（跳过测试）..."
& "$Root\\gradlew.bat" clean | Out-Host
& "$Root\\gradlew.bat" build -x test | Out-Host

Info "生成 installDist..."
& "$Root\\gradlew.bat" :gui:installDist | Out-Host

$InstallDir = "$Root\\gui\\build\\install\\gui"
if (-not (Test-Path $InstallDir)) { Fail "安装目录不存在：$InstallDir" }

$LibDir = "$InstallDir\\lib"
$PluginsDir = "$InstallDir\\plugins"
New-Item -ItemType Directory -Force -Path $PluginsDir | Out-Null

Info "从 settings.gradle.kts 检测插件模块，并移动 JAR 到 plugins/..."
$Settings = Get-Content "$Root\\settings.gradle.kts" -Raw
$Matches = [regex]::Matches($Settings, 'include\\(\\":plugins:([^\\"]+)\\"\\)')
if ($Matches.Count -le 0) { Fail "未能从 settings.gradle.kts 检测到任何插件模块" }

foreach ($m in $Matches) {
  $name = $m.Groups[1].Value
  $jar = "$name.jar"
  $src = Join-Path $LibDir $jar
  $dst = Join-Path $PluginsDir $jar
  if (Test-Path $src) {
    Move-Item -Force $src $dst
    Info "  ✓ $jar"
  } else {
    Warn "  未找到: $src（可能未参与 runtimeClasspath 或已移动）"
  }
}

Info "准备 jpackage 输入目录..."
$JPackageDir = "$Root\\gui\\build\\jpackage"
$InputDir = "$JPackageDir\\input"
Remove-Item -Recurse -Force $JPackageDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $InputDir | Out-Null
New-Item -ItemType Directory -Force -Path "$InputDir\\plugins" | Out-Null

Copy-Item -Path "$LibDir\\*.jar" -Destination $InputDir -Force
Copy-Item -Path "$PluginsDir\\*.jar" -Destination "$InputDir\\plugins" -Force -ErrorAction SilentlyContinue

$DestDir = "$Root\\gui\\build\\distributions"
New-Item -ItemType Directory -Force -Path $DestDir | Out-Null

Info "运行 jpackage..."
& jpackage `
  --type $Type `
  --name "EditorX" `
  --app-version $Version `
  --dest $DestDir `
  --input $InputDir `
  --main-jar "gui.jar" `
  --main-class "editorx.gui.GuiAppKt" | Out-Host

Info "完成：请检查输出目录：$DestDir"
