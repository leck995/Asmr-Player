#!/usr/bin/env bash
# =====================================================================
#  AsmrPlayer packaging for Linux and macOS.  Windows uses package.bat.
#
#   [1] jlink runtime image -> target/app
#   [2] jpackage app-image  -> target/image/AsmrPlayer       (Linux)
#                                 target/image/AsmrPlayer.app (macOS)
#   [3] VLC injection       -> <application dir>/vlc/<platform>
#   [4] packages            -> target/jpackage/AsmrPlayer-<version>.{dmg,pkg}
#                                 target/jpackage/asmrplayer_<version>_amd64.deb
#
#  Every package type is burned from the app-image of step 2 with
#  --app-image, so all packages of one run carry identical content and
#  re-burning them does not repeat the maven/jlink build.
#
#  VLC is copied into the application directory instead of passed with
#  --app-content, because jpackage documents the app-content destination
#  for Windows only. The launcher is given -Dvlc.dir=$APPDIR/vlc, which
#  VlcNativeLoader honours before it falls back to layout guessing, and
#  the injected tree keeps the vlc/ layout of this repository so the
#  platform subdirectory is still picked at runtime. See vlc/README.md.
#
#  deb needs fakeroot, rpm needs rpm-build, dmg and pkg need the Xcode
#  command line tools (also required for --icon). An unsigned bundle does
#  not start on Apple Silicon, so step 3 ad-hoc signs the .app.
# =====================================================================

set -eu

cd -- "$(dirname -- "$0")"

APP_NAME=${APP_NAME:-AsmrPlayer}
APP_VERSION=${APP_VERSION:-}
VENDOR=${VENDOR:-未来道具研究所}
AUTHOR=${AUTHOR:-leck}
MAIN_MODULE=cn.tealc.asmr/cn.tealc.asmr.Main
BUNDLE_ID=cn.tealc.asmr
LINUX_PACKAGE_NAME=${LINUX_PACKAGE_NAME:-asmrplayer}
DEB_MAINTAINER=${DEB_MAINTAINER:-leck995@users.noreply.github.com}
RPM_LICENSE_TYPE=${RPM_LICENSE_TYPE:-GPLv2+}
MENU_GROUP=${MENU_GROUP:-AudioVideo}

JLINK_IMAGE=target/app
IMAGE_DIR=target/image
PKG_DIR=target/jpackage
VLC_DIR=vlc
VLC_STAGE=target/vlc-app-content/vlc
ICON_PNG=src/main/resources/cn/tealc/asmr/image/favicon.png
MAC_ICON=target/icon/AsmrPlayer.icns

die() {
    echo "[ERROR] $*" >&2
    exit 1
}

usage() {
    cat <<EOF

 ${APP_NAME} packaging (Linux and macOS)

 Usage:  package.sh [all|deb|rpm|dmg|pkg|image] [options]

   all           every package type of the current platform
   deb rpm       Linux only
   dmg pkg       macOS only
   image         jlink image + app-image only, no packages

   --skip-build  reuse $JLINK_IMAGE instead of maven + jlink
   --skip-image  reuse the app-image instead of rebuilding it
                 implies --skip-build, because maven clean wipes target
   -v, --verbose pass jpackage --verbose
   -h, --help    show this help

 Environment overrides: APP_NAME APP_VERSION VENDOR LINUX_PACKAGE_NAME
 DEB_MAINTAINER RPM_LICENSE_TYPE MENU_GROUP

 deb needs fakeroot, rpm needs rpm-build, dmg and pkg need the Xcode
 command line tools.
EOF
}

# ------------------------------------------------------------ platform
case $(uname -s) in
    Linux)  OS_KIND=linux ;;
    Darwin) OS_KIND=macos ;;
    *)      OS_KIND=other ;;
esac

# ---------------------------------------------------------------- args
TYPES=
PKG_TYPE=all
DO_BUILD=1
DO_IMAGE=1
VERBOSE=()

while [ $# -gt 0 ]; do
    case $1 in
        all)   PKG_TYPE=all ;;
        image) PKG_TYPE=image ;;
        deb | rpm | dmg | pkg)
            if [ "$OS_KIND" = macos ]; then
                case $1 in deb | rpm) die "$1 is a Linux package type." ;; esac
            else
                case $1 in dmg | pkg) die "$1 is a macOS package type." ;; esac
            fi
            TYPES="$TYPES $1"
            ;;
        --skip-build) DO_BUILD= ;;
        --skip-image) DO_IMAGE= ;;
        -v | --verbose) VERBOSE=(--verbose) ;;
        -h | --help) usage; exit 0 ;;
        *) echo "[ERROR] unknown option: $1" >&2; usage >&2; exit 1 ;;
    esac
    shift
done

if [ "$OS_KIND" = other ]; then
    die "package.sh builds Linux and macOS packages only; use package.bat on Windows."
fi

case $(uname -m) in
    x86_64 | amd64)  CPU=amd64 ;;
    arm64 | aarch64) CPU=aarch64 ;;
    *)               die "unsupported CPU: $(uname -m)" ;;
esac

if [ "$OS_KIND" = macos ]; then
    VLC_PLATFORM="macos-$CPU"
    ALL_TYPES="dmg pkg"
else
    VLC_PLATFORM="linux-$CPU"
    ALL_TYPES="deb rpm"
fi

if [ -z "$TYPES" ] && [ "$PKG_TYPE" != image ]; then
    TYPES=$ALL_TYPES
fi
if [ -z "$DO_IMAGE" ]; then
    # Reusing the app-image must not run maven, because its clean wipes it.
    DO_BUILD=
fi
if [ -z "$TYPES" ] && [ -z "$DO_IMAGE" ]; then
    die "nothing to build: --skip-image is only useful with a package type."
fi

# ------------------------------------------------------------ version
if [ -z "$APP_VERSION" ]; then
    APP_VERSION=$(awk -F'[<>]' '/^[[:space:]]*<version>/{print $3; exit}' pom.xml)
    if [ -z "$APP_VERSION" ]; then
        die "could not read the version from pom.xml; set APP_VERSION."
    fi
fi

COMMON_OPTS=(
    --name "$APP_NAME"
    --app-version "$APP_VERSION"
    --vendor "$VENDOR"
    --copyright "Copyright (C) 2026 $AUTHOR"
    --description "$APP_NAME (author: $AUTHOR)"
)

echo "=========================================="
echo " $APP_NAME $APP_VERSION packaging ($OS_KIND/$CPU)"
echo "=========================================="
if [ -z "$DO_BUILD" ]; then echo "  reuse   jlink image: $JLINK_IMAGE"; fi
if [ -z "$DO_IMAGE" ]; then echo "  reuse   app-image"; fi
if [ -z "$TYPES" ]; then echo "  skip    packages"; fi
echo "  vlc     $VLC_DIR/$VLC_PLATFORM"

# ------------------------------------------------------------ prereqs
if [ -n "$DO_BUILD" ]; then
    command -v mvn >/dev/null 2>&1 || die "mvn not found. Add Maven to PATH."
fi
command -v jpackage >/dev/null 2>&1 || die "jpackage not found. Add the JDK 25 bin directory to PATH."
if [ -f "$VLC_DIR/$VLC_PLATFORM" ]; then
    die "$VLC_DIR/$VLC_PLATFORM should be a directory, not a file."
fi
if [ ! -d "$VLC_DIR/$VLC_PLATFORM" ]; then
    die "bundled VLC not found: $VLC_DIR/$VLC_PLATFORM"
fi
if [ "$OS_KIND" = macos ]; then
    command -v sips >/dev/null 2>&1 || die "sips not found. Install the Xcode command line tools."
    command -v iconutil >/dev/null 2>&1 || die "iconutil not found. Install the Xcode command line tools."
fi

# ---------------------------------------------------------------- [1/4]
if [ -n "$DO_BUILD" ]; then
    echo
    echo "[1/4] Building jlink runtime image..."
    rm -rf -- "$JLINK_IMAGE"
    mvn -B -DskipTests clean compile javafx:jlink || die "maven clean + compile + jlink failed."
fi
if [ ! -x "$JLINK_IMAGE/bin/java" ]; then
    die "runtime image not found: $JLINK_IMAGE (drop --skip-build)."
fi

# ---------------------------------------------------------------- [2/4]
if [ "$OS_KIND" = macos ]; then
    # jpackage needs an .icns, and the repository carries only the Windows
    # .ico plus the 200x200 PNG master.
    echo
    echo "[2/4] Generating the macOS icon..."
    iconset=target/icon/AppIcon.iconset
    rm -rf -- "$iconset" "$MAC_ICON"
    mkdir -p -- "$iconset"
    make_icon() {
        suffix=
        if [ "$1" != "$2" ]; then suffix=@2x; fi
        sips -z "$2" "$2" "$ICON_PNG" --out "$iconset/icon_${1}x${1}$suffix.png" >/dev/null
    }
    make_icon 16 16
    make_icon 16 32
    make_icon 32 32
    make_icon 32 64
    make_icon 128 128
    make_icon 128 256
    make_icon 256 256
    make_icon 256 512
    make_icon 512 512
    make_icon 512 1024
    iconutil -c icns "$iconset" -o "$MAC_ICON" || die "iconutil failed to build $MAC_ICON."
    ICON_OPT=(--icon "$MAC_ICON")
    APP_IMAGE="$IMAGE_DIR/$APP_NAME.app"
    APP_CONTENT_DIR="$APP_IMAGE/Contents/app"
else
    if [ ! -f "$ICON_PNG" ]; then
        die "icon not found: $ICON_PNG"
    fi
    ICON_OPT=(--icon "$ICON_PNG")
    APP_IMAGE="$IMAGE_DIR/$APP_NAME"
    APP_CONTENT_DIR="$APP_IMAGE/lib/app"
fi

if [ -n "$DO_IMAGE" ]; then
    echo
    echo "[2/4] Building jpackage app-image..."
    rm -rf -- "$IMAGE_DIR"
    jpackage ${VERBOSE[@]+"${VERBOSE[@]}"} --type app-image \
        "${COMMON_OPTS[@]}" "${ICON_OPT[@]}" \
        --runtime-image "$JLINK_IMAGE" \
        --module "$MAIN_MODULE" \
        --java-options "-Djavafx.enablePreview=true" \
        --java-options "-Dvlc.dir=\$APPDIR/vlc" \
        --java-options "-XX:+UseZGC" \
        --dest "$IMAGE_DIR" || die "jpackage app-image failed."
fi
if [ ! -d "$APP_IMAGE" ]; then
    die "app-image not found: $APP_IMAGE (run package.sh image first)."
fi
if [ ! -d "$APP_CONTENT_DIR" ]; then
    die "application directory not found: $APP_CONTENT_DIR"
fi

# ---------------------------------------------------------------- [3/4]
echo
echo "[3/4] Injecting bundled VLC into the app-image..."
rm -rf -- "$VLC_STAGE"
mkdir -p -- "$VLC_STAGE"
cp -R -- "$VLC_DIR/$VLC_PLATFORM" "$VLC_STAGE/"
cp -- "$VLC_DIR"/COPYING-*.txt "$VLC_DIR/README.md" "$VLC_STAGE/"
rm -rf -- "$APP_CONTENT_DIR/vlc"
mkdir -p -- "$APP_CONTENT_DIR/vlc"
cp -R -- "$VLC_STAGE/." "$APP_CONTENT_DIR/vlc/"
# macOS keeps libvlc.dylib one level deeper in lib/, so search the platform tree.
if [ -z "$(find "$APP_CONTENT_DIR/vlc/$VLC_PLATFORM" -name 'libvlc.*' -print 2>/dev/null | head -1)" ]; then
    die "no libvlc under $APP_CONTENT_DIR/vlc/$VLC_PLATFORM after injection."
fi
echo "  target  $APP_CONTENT_DIR/vlc/$VLC_PLATFORM"

if [ "$OS_KIND" = macos ] && command -v codesign >/dev/null 2>&1; then
    # Copying files in invalidates whatever signature jpackage left, and
    # Apple Silicon refuses to start unsigned code.
    codesign --force --sign - "$APP_IMAGE" || \
        echo "[WARN] ad-hoc signing failed; $APP_NAME.app may not start on Apple Silicon."
fi

# ---------------------------------------------------------------- [4/4]
if [ -n "$TYPES" ]; then
    echo
    # jpackage 不覆盖同名安装包，先清掉本次版本的历史产物。
    if [ -d "$PKG_DIR" ]; then
        for stale in "$PKG_DIR"/*"$APP_VERSION"*; do
            if [ -e "$stale" ]; then rm -f -- "$stale"; fi
        done
    fi
    for type in $TYPES; do
        echo "[4/4] Building $type package..."
        if [ "$OS_KIND" = macos ]; then
            type_opts=(--mac-package-name "$APP_NAME" --mac-package-identifier "$BUNDLE_ID")
        else
            type_opts=(--linux-shortcut --linux-menu-group "$MENU_GROUP")
            if [ "$type" = deb ]; then
                type_opts=("${type_opts[@]}" --linux-package-name "$LINUX_PACKAGE_NAME" \
                    --linux-deb-maintainer "$DEB_MAINTAINER")
            else
                type_opts=("${type_opts[@]}" --linux-package-name "$LINUX_PACKAGE_NAME" \
                    --linux-rpm-license-type "$RPM_LICENSE_TYPE")
            fi
        fi
        jpackage ${VERBOSE[@]+"${VERBOSE[@]}"} --type "$type" \
            "${COMMON_OPTS[@]}" "${ICON_OPT[@]}" \
            --app-image "$APP_IMAGE" \
            --dest "$PKG_DIR" \
            "${type_opts[@]}" || die "jpackage $type failed."
    done
fi

# --------------------------------------------------------------- result
echo
echo "=========================================="
echo " Done: $APP_NAME $APP_VERSION"
echo "=========================================="
echo "  app-image     $APP_IMAGE"
if [ -d "$PKG_DIR" ]; then
    for file in "$PKG_DIR"/*; do
        if [ -f "$file" ]; then
            printf "  package       %s   %s bytes\n" "$(basename -- "$file")" "$(wc -c < "$file" | tr -d ' ')"
        fi
    done
fi
