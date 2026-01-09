#!/bin/bash

# EditorX 打包脚本
# 用于构建和打包 EditorX 应用程序

set -e  # 遇到错误立即退出

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 输出信息函数
info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

# 检查必要工具
check_requirements() {
    info "检查构建环境..."
    
    if ! command -v java &> /dev/null; then
        error "Java 未安装，请安装 Java 21 或更高版本"
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        warn "Java 版本可能过低，建议使用 Java 21"
    fi
    
    if [ ! -f "./gradlew" ]; then
        error "未找到 gradlew 脚本"
    fi
    
    chmod +x ./gradlew
}

# 清理之前的构建
clean_build() {
    info "清理之前的构建..."
    ./gradlew clean
}

# 构建项目
build_project() {
    info "构建项目..."
    ./gradlew build -x test
}

# 创建分发包
create_distribution() {
    info "创建分发包..."
    
    # 先创建标准的 installDist
    ./gradlew :gui:installDist
    
    # 自动从 settings.gradle.kts 中读取插件模块列表
    info "从 settings.gradle.kts 检测插件模块..."
    local plugin_jars=()
    
    # 解析 settings.gradle.kts 文件，提取所有 :plugins:xxx 模块
    while IFS= read -r line; do
        # 匹配 include(":plugins:xxx") 格式
        if [[ "$line" =~ include\(\":plugins:([^\"]+)\"\) ]]; then
            local module_name="${BASH_REMATCH[1]}"
            plugin_jars+=("${module_name}.jar")
            info "  ✓ 检测到插件模块: $module_name -> ${module_name}.jar"
        fi
    done < settings.gradle.kts
    
    if [ ${#plugin_jars[@]} -eq 0 ]; then
        error "未能从 settings.gradle.kts 检测到任何插件模块"
    fi
    
    info "共检测到 ${#plugin_jars[@]} 个插件模块"
    
    local install_dir="gui/build/install/gui"
    
    if [ ! -d "$install_dir" ]; then
        error "安装目录不存在: $install_dir"
    fi
    
    # 创建 plugins 目录
    local plugins_dir="$install_dir/plugins"
    mkdir -p "$plugins_dir"
    
    # 移动插件 JAR 到 plugins 目录
    local lib_dir="$install_dir/lib"
    if [ ! -d "$lib_dir" ]; then
        error "lib 目录不存在: $lib_dir"
    fi
    
    info "移动插件 JAR 到 plugins 目录..."
    for plugin_jar in "${plugin_jars[@]}"; do
        local source_file="$lib_dir/$plugin_jar"
        if [ -f "$source_file" ]; then
            mv "$source_file" "$plugins_dir/"
            info "  ✓ $plugin_jar"
        else
            warn "  未找到插件: $plugin_jar"
        fi
    done
    
    # 验证插件 JAR 已从 lib 目录移除
    info "验证插件 JAR 移动结果..."
    local remaining_plugins=0
    for plugin_jar in "${plugin_jars[@]}"; do
        if [ -f "$lib_dir/$plugin_jar" ]; then
            warn "  警告: $plugin_jar 仍在 lib 目录中"
            remaining_plugins=$((remaining_plugins + 1))
        fi
    done
    
    if [ $remaining_plugins -eq 0 ]; then
        info "  ✓ 所有插件 JAR 已移动到 plugins 目录"
    else
        error "部分插件 JAR 移动失败，请检查"
    fi
    
    # 若需要：在分发包中内置 Java 运行时（jbr/），实现下载即用
    if [ "$BUNDLE_JAVA" = true ]; then
        bundle_java_runtime "$install_dir"
        patch_start_scripts "$install_dir"
    fi

    # 删除旧的 zip 和 tar 文件（如果存在）
    local zip_file="gui/build/distributions/gui.zip"
    if [ "$BUNDLE_JAVA" = true ]; then
        zip_file="gui/build/distributions/gui-bundled-java.zip"
    fi
    local tar_file="gui/build/distributions/gui.tar"
    if [ -f "$zip_file" ]; then
        rm -f "$zip_file"
        info "已删除旧的 zip 文件"
    fi
    if [ -f "$tar_file" ]; then
        rm -f "$tar_file"
        info "已删除旧的 tar 文件"
    fi
    
    # 创建 zip 包
    info "创建 ZIP 包..."
    local zip_dir="$(dirname "$zip_file")"
    mkdir -p "$zip_dir"
    
    # 获取当前工作目录（脚本所在目录）
    local current_dir="$(pwd)"
    local abs_zip_file="${current_dir}/${zip_file}"
    
    # 进入安装目录的父目录，以便 zip 中包含正确的目录结构（gui/ 作为根）
    cd "$(dirname "$install_dir")"
    zip -r "$abs_zip_file" "$(basename "$install_dir")" > /dev/null 2>&1
    local zip_exit_code=$?
    cd "$current_dir"
    
    if [ $zip_exit_code -ne 0 ]; then
        error "ZIP 包创建失败"
    fi
    
    # 验证 zip 文件
    if [ ! -f "$zip_file" ]; then
        error "ZIP 包创建失败: $zip_file"
    fi
    
    # 验证 zip 包中 lib 目录不包含插件 JAR
    info "验证 ZIP 包内容..."
    local zip_plugin_in_lib=$(unzip -l "$zip_file" 2>/dev/null | grep -E "gui/lib/(android|git|json|yaml|xml|smali|i18n-(zh|en))\.jar" | wc -l | tr -d ' ')
    if [ "$zip_plugin_in_lib" -gt 0 ]; then
        warn "警告: ZIP 包中 lib 目录仍包含 $zip_plugin_in_lib 个插件 JAR"
        info "ZIP 包 lib 目录内容："
        unzip -l "$zip_file" 2>/dev/null | grep "gui/lib/.*\.jar" | sed 's/^/    /'
    else
        info "  ✓ ZIP 包中 lib 目录不包含插件 JAR"
    fi
    
    info "分发包创建成功："
    info "  - $zip_file"
}

java_major_version() {
    local java_bin="$1"
    # 兼容输出：openjdk version "21.0.6" / java version "1.8.0_..."
    local version_line
    version_line=$("$java_bin" -version 2>&1 | head -n 1)
    local ver
    ver=$(echo "$version_line" | sed -n 's/.*\"\([0-9][0-9]*\)\..*\".*/\1/p')
    if [ -z "$ver" ]; then
        ver=$(echo "$version_line" | sed -n 's/.*\"\([0-9][0-9]*\)\".*/\1/p')
    fi
    if [ -z "$ver" ]; then
        echo ""
        return 0
    fi
    # 处理 1.8 这种情况
    if [ "$ver" = "1" ]; then
        ver=$(echo "$version_line" | sed -n 's/.*\"1\.\([0-9][0-9]*\)\..*\".*/\1/p')
    fi
    echo "$ver"
}

assert_java_at_least_21() {
    local java_home="$1"
    local java_bin="$java_home/bin/java"
    local major
    major="$(java_major_version "$java_bin")"
    if [ -z "$major" ]; then
        warn "无法解析 Java 版本：$java_bin（将继续尝试，但运行时可能失败）"
        return 0
    fi
    if [ "$major" -lt 21 ]; then
        error "内置 Java 版本过低（需要 >= 21，当前=$major）：$java_home"
    fi
}

detect_java_home() {
    normalize_dir() {
        local p="$1"
        if [ -z "$p" ]; then
            echo ""
            return 0
        fi
        if command -v realpath &> /dev/null; then
            realpath "$p" 2>/dev/null || echo "$p"
            return 0
        fi
        (cd -P "$p" > /dev/null 2>&1 && pwd) || echo "$p"
    }

    if [ -n "$JAVA_HOME_OVERRIDE" ]; then
        normalize_dir "$JAVA_HOME_OVERRIDE"
        return 0
    fi
    if [ -n "$JAVA_HOME" ]; then
        normalize_dir "$JAVA_HOME"
        return 0
    fi
    local detected
    detected=$(java -XshowSettings:properties -version 2>&1 | awk -F' = ' '/java.home =/ {print $2; exit}' | tr -d '\r')
    normalize_dir "$detected"
}

bundle_java_runtime() {
    local install_dir="$1"
    local runtime_dir="$install_dir/jbr"

    local java_home
    java_home="$(detect_java_home)"
    if [ -z "$java_home" ]; then
        error "无法检测 Java Home，请使用 --java-home 指定"
    fi
    if [ ! -x "$java_home/bin/java" ]; then
        error "Java Home 无效（未找到 bin/java）：$java_home"
    fi

    assert_java_at_least_21 "$java_home"

    info "内置 Java 运行时：$java_home -> $runtime_dir"
    rm -rf "$runtime_dir"
    mkdir -p "$runtime_dir"

    if command -v rsync &> /dev/null; then
        rsync -a "$java_home/" "$runtime_dir/"
    else
        cp -R "$java_home/"* "$runtime_dir/"
    fi

    if [ ! -x "$runtime_dir/bin/java" ]; then
        error "内置 Java 运行时复制失败（未找到 jbr/bin/java）"
    fi
}

patch_start_scripts() {
    local install_dir="$1"
    local unix_script="$install_dir/bin/gui"
    local win_script="$install_dir/bin/gui.bat"

    if [ -f "$unix_script" ]; then
        python3 - "$unix_script" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
data = path.read_bytes()
text = data.decode("utf-8", errors="replace")

marker = "EDITORX_BUNDLED_RUNTIME"
if marker in text:
    sys.exit(0)

newline = "\r\n" if b"\r\n" in data else "\n"
needle = "# Determine the Java command to use to start the JVM."
idx = text.find(needle)
if idx < 0:
    raise SystemExit(f"无法补丁启动脚本（未找到锚点）：{path}")

insertion_lines = [
    "",
    "# EditorX: bundled runtime support (EDITORX_BUNDLED_RUNTIME)",
    'if [ -z "${JAVA_HOME}" ] && [ -x "$APP_HOME/jbr/bin/java" ]; then',
    '    JAVA_HOME="$APP_HOME/jbr"',
    "fi",
    "",
]
insertion = newline.join(insertion_lines)
text = text.replace(needle, insertion + needle, 1)
path.write_bytes(text.encode("utf-8"))
PY
        chmod +x "$unix_script"
    fi

    if [ -f "$win_script" ]; then
        python3 - "$win_script" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
data = path.read_bytes()
text = data.decode("utf-8", errors="replace")

marker = "EDITORX_BUNDLED_RUNTIME"
if marker in text:
    sys.exit(0)

newline = "\r\n" if b"\r\n" in data else "\n"
needle = "@rem Find java.exe"
idx = text.find(needle)
if idx < 0:
    raise SystemExit(f"无法补丁 Windows 启动脚本（未找到锚点）：{path}")

insertion_lines = [
    "",
    "@rem EditorX: bundled runtime support (EDITORX_BUNDLED_RUNTIME)",
    "if not defined JAVA_HOME (",
    '  if exist "%APP_HOME%\\\\jbr\\\\bin\\\\java.exe" set "JAVA_HOME=%APP_HOME%\\\\jbr"',
    ")",
    "",
]
insertion = newline.join(insertion_lines)
text = text.replace(needle, insertion + needle, 1)
path.write_bytes(text.encode("utf-8"))
PY
    fi
}

# 生成 macOS .app（可选）
create_macos_app() {
    # 仅在 macOS 上执行
    if [[ "$(uname -s)" != "Darwin" ]]; then
        warn "当前系统非 macOS，跳过 .app 生成"
        return 0
    fi

    info "生成 macOS .app（jpackage）..."

    if ! command -v jpackage &> /dev/null; then
        error "未找到 jpackage，请安装包含 jpackage 的 JDK（或将 jpackage 加入 PATH）"
    fi

    local install_dir="gui/build/install/gui"
    if [ ! -d "$install_dir" ]; then
        error "安装目录不存在: $install_dir"
    fi

    local jpackage_dir="gui/build/jpackage"
    local input_dir="$jpackage_dir/input"
    local iconset_dir="$jpackage_dir/EditorX.iconset"
    local icns_file="$jpackage_dir/EditorX.icns"
    local out_dir="gui/build/distributions"

    rm -rf "$jpackage_dir"
    mkdir -p "$input_dir"

    # 拷贝应用依赖（lib/*.jar -> input/）
    cp "$install_dir/lib/"*.jar "$input_dir/"

    # 拷贝插件（plugins/*.jar -> input/plugins/）
    if [ -d "$install_dir/plugins" ]; then
        mkdir -p "$input_dir/plugins"
        cp "$install_dir/plugins/"*.jar "$input_dir/plugins/" 2>/dev/null || true
    fi

    # 生成 icns 图标
    local icon_src="gui/src/main/resources/icon.png"
    if [ ! -f "$icon_src" ]; then
        warn "未找到 $icon_src，跳过 --icon（.app 将使用默认图标）"
        icns_file=""
    else
        rm -rf "$iconset_dir"
        mkdir -p "$iconset_dir"

        # iconutil 需要固定命名（icon_16x16.png 等）
        # 说明：源图当前为 500x500，会按需缩放生成各尺寸。
        sips -z 16 16   "$icon_src" --out "$iconset_dir/icon_16x16.png"       >/dev/null 2>&1
        sips -z 32 32   "$icon_src" --out "$iconset_dir/icon_16x16@2x.png"    >/dev/null 2>&1
        sips -z 32 32   "$icon_src" --out "$iconset_dir/icon_32x32.png"       >/dev/null 2>&1
        sips -z 64 64   "$icon_src" --out "$iconset_dir/icon_32x32@2x.png"    >/dev/null 2>&1
        sips -z 128 128 "$icon_src" --out "$iconset_dir/icon_128x128.png"     >/dev/null 2>&1
        sips -z 256 256 "$icon_src" --out "$iconset_dir/icon_128x128@2x.png"  >/dev/null 2>&1
        sips -z 256 256 "$icon_src" --out "$iconset_dir/icon_256x256.png"     >/dev/null 2>&1
        sips -z 512 512 "$icon_src" --out "$iconset_dir/icon_256x256@2x.png"  >/dev/null 2>&1
        sips -z 512 512 "$icon_src" --out "$iconset_dir/icon_512x512.png"     >/dev/null 2>&1
        sips -z 1024 1024 "$icon_src" --out "$iconset_dir/icon_512x512@2x.png" >/dev/null 2>&1

        if command -v iconutil &> /dev/null; then
            iconutil -c icns "$iconset_dir" -o "$icns_file" >/dev/null 2>&1 || true
        else
            warn "未找到 iconutil，跳过生成 .icns（.app 将使用默认图标）"
            icns_file=""
        fi
    fi

    # 使用当前或指定的 Java Home 作为 runtime-image（确保能运行当前编译产物）
    local runtime_home
    runtime_home="$(detect_java_home)"
    if [ -z "$runtime_home" ] || [ ! -x "$runtime_home/bin/java" ]; then
        error "无法解析 java.home 或找到 $runtime_home/bin/java；请确保 PATH 中的 java 可用"
    fi
    assert_java_at_least_21 "$runtime_home"

    # 清理旧的 .app
    rm -rf "$out_dir/EditorX.app"

    # 生成 app-image（.app）
    local jpackage_args=(
        --type app-image
        --name "EditorX"
        --dest "$out_dir"
        --input "$input_dir"
        --main-jar "gui.jar"
        --main-class "editorx.gui.GuiAppKt"
        --runtime-image "$runtime_home"
    )
    if [ -n "$VERSION" ]; then
        jpackage_args+=( --app-version "$VERSION" )
    fi
    if [ -n "$icns_file" ] && [ -f "$icns_file" ]; then
        jpackage_args+=( --icon "$icns_file" )
    fi

    jpackage "${jpackage_args[@]}"

    if [ ! -d "$out_dir/EditorX.app" ]; then
        error ".app 生成失败：$out_dir/EditorX.app"
    fi

    # 调整目录结构（按你的要求）：
    # - 保留 `runtime/`（内置 JDK）
    # - 移除 `jbr`（不创建/不保留）
    # - 移除 `app/`，使用 `lib/` 作为 jar 目录
    # - 插件放在外层 `plugins/`（不放在 lib/plugins）
    local contents_dir="$out_dir/EditorX.app/Contents"
    rm -f "$contents_dir/jbr" 2>/dev/null || true

    if [ -d "$contents_dir/app" ]; then
        rm -rf "$contents_dir/lib"
        mv "$contents_dir/app" "$contents_dir/lib"
    fi

    if [ -d "$contents_dir/lib/plugins" ]; then
        rm -rf "$contents_dir/plugins"
        mv "$contents_dir/lib/plugins" "$contents_dir/plugins"
    fi

    # 替换可执行入口：让 Finder 启动时使用 runtime + lib 布局
    local mac_exec="$contents_dir/MacOS/EditorX"
    if [ -f "$mac_exec" ] && [ ! -f "$mac_exec.bin" ]; then
        mv "$mac_exec" "$mac_exec.bin"
    fi
    cat > "$mac_exec" <<'EOF'
#!/bin/sh
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
CONTENTS_DIR="$(cd "$DIR/.." && pwd)"

JAVA_BIN="$CONTENTS_DIR/runtime/Contents/Home/bin/java"
if [ -x "$JAVA_BIN" ]; then
  :
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  JAVA_BIN="java"
fi

CLASSPATH="$CONTENTS_DIR/lib/*"
exec "$JAVA_BIN" ${JAVA_OPTS} ${EDITORX_OPTS} -Deditorx.home="$CONTENTS_DIR" -classpath "$CLASSPATH" editorx.gui.GuiAppKt "$@"
EOF
    chmod +x "$mac_exec"

    mkdir -p "$contents_dir/bin"
    cat > "$contents_dir/bin/editorx" <<'EOF'
#!/bin/sh
DIR="$(cd "$(dirname "$0")" && pwd)"
APP_HOME="$(cd "$DIR/.." && pwd)"
exec "$APP_HOME/MacOS/EditorX" "$@"
EOF
    chmod +x "$contents_dir/bin/editorx"

    # 拷贝内置工具（apktool.jar / smali.jar 等），保证 .app 内可用
    if [ -d "$install_dir/tools" ]; then
        rm -rf "$contents_dir/tools"
        cp -R "$install_dir/tools" "$contents_dir/tools"
        chmod +x "$contents_dir/tools/apktool" 2>/dev/null || true
    fi

    # 修改过 bundle 内容后需要重新签名，否则可能提示“已损坏”
    if [ -x "/usr/bin/codesign" ]; then
        /usr/bin/codesign -s - --force --deep "$out_dir/EditorX.app"
    fi

    # 生成可分发的 zip（macOS 推荐用 ditto 保留资源 fork）
    local app_zip="$out_dir/EditorX-macOS.app.zip"
    rm -f "$app_zip"
    if command -v ditto &> /dev/null; then
        (cd "$out_dir" && ditto -c -k --sequesterRsrc --keepParent "EditorX.app" "$(basename "$app_zip")")
    else
        warn "未找到 ditto，回退到 zip -r 打包 .app（可能丢失部分元数据）"
        (cd "$out_dir" && zip -r "$(basename "$app_zip")" "EditorX.app" > /dev/null 2>&1)
    fi

    info "macOS 产物已生成："
    info "  - $out_dir/EditorX.app"
    info "  - $app_zip"
}

# 创建发布目录
create_release_dir() {
    local version=${1:-"dev"}
    local release_dir="release/editorx-${version}"
    
    info "创建发布目录: $release_dir"
    mkdir -p "$release_dir"
    
    # 复制分发包（zip）
    if [ -f "gui/build/distributions/gui.zip" ]; then
        cp "gui/build/distributions/gui.zip" "$release_dir/"
    fi
    if [ -f "gui/build/distributions/gui-bundled-java.zip" ]; then
        cp "gui/build/distributions/gui-bundled-java.zip" "$release_dir/"
    fi
    # 若存在 macOS .app zip，也一并复制（可选）
    if [ -f "gui/build/distributions/EditorX-macOS.app.zip" ]; then
        cp "gui/build/distributions/EditorX-macOS.app.zip" "$release_dir/"
    fi
    
    # 获取版本信息（如果可能）
    if command -v git &> /dev/null && git rev-parse --git-dir > /dev/null 2>&1; then
        local git_version=$(git describe --tags --always 2>/dev/null || git rev-parse --short HEAD)
        echo "$git_version" > "$release_dir/VERSION"
    fi
    
    # 创建构建信息
    cat > "$release_dir/BUILD_INFO" << EOF
Build Date: $(date -u +"%Y-%m-%d %H:%M:%S UTC")
Build User: $(whoami)
Java Version: $(java -version 2>&1 | head -n 1)
Gradle Version: $(./gradlew --version 2>&1 | grep "Gradle" | head -n 1)
EOF
    
    info "发布文件已创建到: $release_dir"
}

# 显示帮助信息
show_help() {
    cat << EOF
EditorX 打包脚本

用法: $0 [选项]

选项:
    -v, --version VERSION    指定版本号（用于发布目录命名）
    -c, --clean               清理构建（默认会清理）
    -b, --build-only          仅构建，不创建分发包
    --bundle-java             在 zip 分发包中内置 Java 运行时（生成 jbr/，用户可下载即用）
    --java-home PATH          指定要内置的 Java Home（默认自动检测 java.home / JAVA_HOME）
    --mac-app                 额外生成 macOS .app（需要 macOS + jpackage）
    -h, --help                显示此帮助信息

示例:
    $0                        # 标准打包
    $0 -v 1.0.0              # 指定版本号打包
    $0 -b                    # 仅构建，不创建分发包
    $0 --bundle-java         # zip 分发包内置 Java 运行时
    $0 --mac-app             # 标准打包 + 生成 macOS .app
    $0 --clean               # 清理并打包

EOF
}

# 解析命令行参数
VERSION=""
CLEAN=true
BUILD_ONLY=false
MAC_APP=false
BUNDLE_JAVA=false
JAVA_HOME_OVERRIDE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--version)
            VERSION="$2"
            shift 2
            ;;
        -c|--clean)
            CLEAN=true
            shift
            ;;
        -b|--build-only)
            BUILD_ONLY=true
            shift
            ;;
        --bundle-java)
            BUNDLE_JAVA=true
            shift
            ;;
        --java-home)
            JAVA_HOME_OVERRIDE="$2"
            shift 2
            ;;
        --mac-app)
            MAC_APP=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            error "未知选项: $1"
            ;;
    esac
done

# 主流程
main() {
    info "开始打包 EditorX..."
    
    check_requirements
    
    if [ "$CLEAN" = true ]; then
        clean_build
    fi
    
    build_project
    
    if [ "$BUILD_ONLY" = false ]; then
        create_distribution

        if [ "$MAC_APP" = true ]; then
            create_macos_app
        fi
        
        if [ -n "$VERSION" ]; then
            create_release_dir "$VERSION"
        fi
    fi
    
    info "打包完成！"
}

# 执行主流程
main
