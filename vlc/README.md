# 内置 VLC 运行库

本目录存放各平台随程序分发的 VLC 运行库，由 `VlcNativeLoader` 在启动时自动发现，
使程序在未安装 VLC 的机器上也能播放。若本目录缺失对应平台，则回退到系统已安装的 VLC。

## 目录结构

```
vlc/
  windows-amd64/       libvlc.dll + libvlccore.dll + plugins/        (VLC 3.0.23 x64)
  macos-aarch64/       lib/libvlc.dylib + plugins/                   (VLC 3.0.23 Apple Silicon)
  macos-x86_64/        lib/libvlc.dylib + plugins/                   (VLC 3.0.23 Intel)
  linux-amd64/         libvlc.so.5 + libvlccore.so.9 + lib/ + plugins/ (VLC 3.0.24-rc1)
```

macOS 的插件目录由 vlcj 按 `<库目录>/../plugins` 推导；Linux/Windows 的插件目录为库目录下的 `plugins/`。
`lib/` 中是 Linux 版 VLC 的第三方依赖闭包，所有 ELF 已写入 `$ORIGIN` 相对 RPATH，可整体搬移。

## 平台要求

- **Windows**：Windows 10/11 x64。
- **macOS**：Apple Silicon 需 macOS 11+；Intel 需 macOS 10.7+（VLC 3.0.23 官方构建）。
- **Linux**：x86_64，glibc 2.38+（Ubuntu 24.04 / Debian 13 / Fedora 39 或更新）。
  `lib/` 已包含 VLC 的第三方依赖（含 libfaad、libFLAC、libmpg123、libvorbis、libopus 等），
  但以下基础库仍由系统提供（主流桌面发行版默认具备）：
  `libdbus-1.so.3`、`libsystemd.so.0`、`libapparmor.so.1`、`libbsd.so.0`、`libgnutls.so.30`。
  其中 `libgnutls` 用于 HTTPS 直链播放（OpenList、`.strm`）。

## 精简说明

仅保留音频播放链路所需的模块，已移除：

- 视频、字幕、界面、Lua、可视化、转码与输出等全部插件；
- `libavcodec` 及 FFmpeg 编解码依赖（精简后单个平台约 15–18 MB）；
- 除 HTTP/HTTPS 外的网络协议（FTP、SFTP、RTSP、UDP、SMB 等）。

保留的格式：

- **MP3**（mpg123/mad）、**WAV**（PCM/ADPCM/G.711）、**FLAC**、**OGG**（Vorbis/Opus/Speex）；
- **M4A/M4B**（AAC-LC，经 libfaad 解码）；
- AIFF、AU、CAF 等无压缩/简单音频容器；
- 上述音频编码封装在 AVI/MKV 中的音轨。

不再支持：WMA、RealAudio（`.ra`/`.rm`）、ALAC、AC-3/DTS、MIDI，以及 HLS/DASH、
RTSP/RTMP 等流媒体协议。DRM 保护的 `.m4p` 也不支持。
`VlcNativeLoader` 在存在内置库时不会回退到系统 VLC，因此这些格式即使系统装有完整 VLC 也无法播放。

## 许可与源码

VLC 以 GPLv2+ 分发，libvlc 及部分库为 LGPLv2.1+；FFmpeg 等第三方库各有其许可。
许可全文见同目录 `COPYING-GPL-2.0.txt`、`COPYING-LGPL-2.1.txt`。

- 项目与源码：https://www.videolan.org/ , https://code.videolan.org/videolan/vlc
- 官方构建下载：https://www.videolan.org/vlc/download-macosx.html
- Linux 构建来源：VideoLAN 官方 Snap（`vlc` 3.0.24-rc1, core24）
- Linux 版 `libfaad.so.2` 来自 Ubuntu 24.04 `libfaad2` 2.11.2-1build1，
  源码：https://github.com/knik0/faad2（GPLv2+）
