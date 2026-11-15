---
name: ikonli-fonticon-check
description: Use whenever writing or editing JavaFX FXML or Java code that uses FontIcon with ikonli icon literals (e.g. mdomz-vpn_key, mdoal-info, mdoal-lock). Triggers on any mention of FontIcon, iconLiteral, ikonli, Material2OutlinedAL, Material2OutlinedMZ, MaterialDesign2, or icon literal strings in FXML/Java. Prevents the runtime crash "Icon description 'xxx' is invalid!" by validating icon literals against the actual ikonli enum before code is written.
---

# Ikonli FontIcon Literal 检查

本项目的 JavaFX 界面大量使用 `org.kordamp.ikonli.javafx.FontIcon` 通过 `iconLiteral` 指定图标。ikonli 的图标枚举**按字母范围分段**，用错前缀或图标名会导致运行时 `IllegalArgumentException: Icon description 'xxx' is invalid!`，FXML 能加载但一旦碰到无效图标就崩溃。

## 核心规则：前缀 = 字母范围

本项目用到的两个 ikonli 枚举包都是 Material2Outlined 系列，按图标名的首字母范围划分：

| 前缀 | 枚举类 | 图标名首字母范围 |
|------|--------|-----------------|
| `mdoal-` | `Material2OutlinedAL` | **A–L**（如 `lock`, `info`, `image`, `cloud`, `keyboard`, `favorite`） |
| `mdomz-` | `Material2OutlinedMZ` | **M–Z**（如 `masks`, `person`, `photo`, `storage`, `update`, `sync`） |

**判断方法**：看图标名的首字母——A 到 L 用 `mdoal-`，M 到 Z 用 `mdomz-`。

> `l` 开头（如 `lock`, `language`, `keyboard`）属于 **AL 包**（`mdoal-`），不是 MZ。
> `m` 开头（如 `masks`, `menu`, `mic`）属于 **MZ 包**（`mdomz-`）。

## 命名格式：下划线，不是连字符

ikonli 的 icon description 使用**下划线**分隔多词图标名，不是连字符。description 就是枚举构造时传入的原始字符串，格式为 `前缀-枚举名小写`，枚举名中的下划线保留不变：

- ✅ `mdomz-vpn_key`、`mdoal-format_bold`、`mdomz-rounded_corner`、`mdoal-blur_on`
- ❌ `mdomz-vpn-key`、`mdoal-format-bold`、`mdomz-rounded-corner`、`mdoal-blur-on`（连字符会报 `Icon description is invalid!`）

枚举常量是 `VPN_KEY`，description 是 `mdomz-vpn_key`（小写，保留下划线）。单词图标没有分隔符：`mdoal-info`、`mdomz-save`、`mdomz-sync`。

## FXML 中 Tile 的 action 子元素

`Tile` 继承自 `Control`，不像 `Pane` 有默认的 `children` 属性。在 FXML 中，Tile 内部的控件**必须**用 `<action>` 标签显式包裹，和 `<graphic>` 一样：

```xml
<!-- ✅ 正确：用 <action> 包裹控件 -->
<Tile title="启动密码" description="..." styleClass="setting-tile">
   <graphic><FontIcon iconLiteral="mdoal-lock" /></graphic>
   <action><JFXToggleButton fx:id="openPassword" text=" " /></action>
</Tile>

<!-- ❌ 错误：直接放控件会导致 "Element does not define a default property" -->
<Tile title="启动密码" description="..." styleClass="setting-tile">
   <graphic><FontIcon iconLiteral="mdoal-lock" /></graphic>
   <JFXToggleButton fx:id="openPassword" text=" " />
</Tile>
```

多个控件放在一个 `<action>` 内的容器中即可：

```xml
<action>
   <VBox styleClass="setting-tile-action-box">
      <JFXToggleButton fx:id="openProxy" text=" " />
      <HBox spacing="5.0">
         <TextField fx:id="proxyHostTextField" promptText="地址" />
         <TextField fx:id="proxyPortTextField" promptText="端口" />
      </HBox>
   </VBox>
</action>
```

## 验证方法（必须在写入代码前执行）

从 Maven 本地仓库提取枚举的实际 description 字符串列表（通过 javap -c 读取常量池中的字符串字面量），逐一核对：

```bash
# 提取 Material2OutlinedMZ 的全部有效 icon description（M-Z 范围）
cd /tmp && rm -rf ikonli_check && mkdir ikonli_check && cd ikonli_check
jar xf "C:/Users/yiqiu/.m2/repository/org/kordamp/ikonli/ikonli-material2-pack/12.3.1/ikonli-material2-pack-12.3.1.jar"
javap -p -c org.kordamp.ikonli.material2.Material2OutlinedMZ | sed 's/\r//g' \
  | grep -oP '(?<=// String )mdomz-[a-z0-9_]+' | sort -u > mdomz_desc.txt
javap -p -c org.kordamp.ikonli.material2.Material2OutlinedAL | sed 's/\r//g' \
  | grep -oP '(?<=// String )mdoal-[a-z0-9_]+' | sort -u > mdoal_desc.txt
```

> **注意**：不要用 `javap -constants` 提取枚举名再转下划线/连字符——那种方式得到的是枚举常量名（如 `VPN_KEY`），转小写后格式不一定与 description 一致。必须用 `javap -p -c` 从字节码常量池中提取实际的 description 字符串（即 `// String mdomz-vpn_key` 注释中的值）。

验证某个图标是否存在：

```bash
# 用 bash 逐行匹配（grep -Fx 在某些环境的 ugrep 下有 bug）
found=false
while IFS= read -r line; do [ "$line" = "mdomz-vpn_key" ] && found=true && break; done < mdomz_desc.txt
$found && echo "OK" || echo "NOT FOUND"
```

批量验证 FXML 中所有图标：

```bash
while IFS= read -r literal; do
  found=false
  while IFS= read -r line; do [ "$line" = "$literal" ] && found=true && break; done < mdomz_desc.txt
  while IFS= read -r line; do [ "$line" = "$literal" ] && found=true && break; done < mdoal_desc.txt
  $found || echo "FAIL: $literal"
done < <(grep -oP 'iconLiteral="\K[^"]+' path/to/YourUI.fxml | sort -u)
```

## 常见易错图标

以下图标容易用错前缀或名称：

| 想表达 | ❌ 错误 | ✅ 正确 | 原因 |
|--------|--------|--------|------|
| 锁 | `mdomz-lock` | `mdoal-lock` | lock 以 l 开头，属 AL |
| 钥匙/VPN | `mdomz-key` | `mdomz-vpn_key` | 没有 key，用 vpn_key |
| 面具 | `mdomz-mask` | `mdomz-masks` | 枚举名是 MASKS（复数） |
| 历史 | `mdomz-history` | `mdoal-history` | history 以 h 开头，属 AL |
| 信息 | `mdomz-info` | `mdoal-info` | info 以 i 开头，属 AL |
| 图片 | `mdomz-image` | `mdoal-image` | image 以 i 开头，属 AL |
| 云 | `mdomz-cloud` | `mdoal-cloud` | cloud 以 c 开头，属 AL |
| 同步 | `mdomz-cloud_sync` | `mdomz-sync` | 没有 cloud_sync，用 sync |
| 宽高比 | `mdomz-width` | `mdoal-aspect_ratio` | 没有 width，用 aspect_ratio |
| 动画 | `mdomz-animation` | `mdomz-play_arrow` | 没有 animation，用 play_arrow |
| 许可 | `mdomz-license` | `mdoal-gavel` | 没有 license，用 gavel（法槌） |
| 网格 | `mdomz-grid_view` | `mdoal-grid_on` | 没有 grid_view，用 grid_on |
| 连字符格式 | `mdomz-vpn-key` | `mdomz-vpn_key` | description 用下划线，不用连字符 |
| 连字符格式 | `mdoal-format-bold` | `mdoal-format_bold` | 同上 |
| 连字符格式 | `mdomz-rounded-corner` | `mdomz-rounded_corner` | 同上 |

## 其他 ikonli 包

本项目还用到了 `MaterialDesign2`（前缀 `mdomz-` 和 `mdoal-` 以外的图标用全名如 `MaterialDesignB.BRIEFCASE_VARIANT_OUTLINE`），在 Java 代码中直接传枚举常量（如 `new FontIcon(MaterialDesignB.BOOKSHELF)`），不受 literal 字符串验证约束。只有在 FXML 中通过 `iconLiteral="..."` 字符串引用时才需要本 skill 的验证流程。
