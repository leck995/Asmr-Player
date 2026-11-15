package cn.tealc.asmr.subtitle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于 OpenCC 通用简繁词典的字幕文本转换器。
 * <p>
 * 转换顺序为词组最长匹配，再进行单字兜底，避免逐字转换造成语义错误。
 * </p>
 */
final class SubtitleChineseConverter {

    private static final String RESOURCE_ROOT = "/cn/tealc/asmr/subtitle/opencc/";

    private final Dictionary s2t = new Dictionary(
            "STPhrases.txt", "STCharacters.txt", "CJK_Compatibility_Ideographs.txt");
    private final Dictionary t2s = new Dictionary(
            "TSPhrases.txt", "TSCharacters.txt", "CJK_Compatibility_Ideographs.txt");

    /**
     * 转换指定文本。
     *
     * @param source 原始文本
     * @param mode   显示模式
     * @return 转换后的文本
     */
    String convert(String source, SubtitleScriptMode mode) {
        if (source == null || source.isEmpty() || mode == null || mode == SubtitleScriptMode.ORIGINAL) {
            return source;
        }
        return switch (mode) {
            case SIMPLIFIED -> t2s.convert(source);
            case TRADITIONAL -> s2t.convert(source);
            case ORIGINAL -> source;
        };
    }

    /**
     * 单方向词典，延迟加载且线程安全。
     */
    private static final class Dictionary {

        private final String phraseResource;
        private final String characterResource;
        private final String normalizationResource;
        private volatile Mapping mapping;

        private Dictionary(String phraseResource, String characterResource, String normalizationResource) {
            this.phraseResource = phraseResource;
            this.characterResource = characterResource;
            this.normalizationResource = normalizationResource;
        }

        private String convert(String source) {
            return getMapping().convert(source);
        }

        private Mapping getMapping() {
            Mapping current = mapping;
            if (current == null) {
                synchronized (this) {
                    current = mapping;
                    if (current == null) {
                        current = Mapping.load(phraseResource, characterResource, normalizationResource);
                        mapping = current;
                    }
                }
            }
            return current;
        }
    }

    /**
     * 已加载词典的数据结构。
     */
    private static final class Mapping {

        private final TrieNode phraseRoot = new TrieNode();
        private final Map<Integer, String> characters = new HashMap<>();

        private static Mapping load(String phraseResource, String characterResource, String normalizationResource) {
            Mapping mapping = new Mapping();
            mapping.loadPhrases(normalizationResource);
            mapping.loadPhrases(phraseResource);
            mapping.loadCharacters(characterResource);
            return mapping;
        }

        private void loadPhrases(String resourceName) {
            forEachEntry(resourceName, (source, target) -> {
                TrieNode node = phraseRoot;
                for (int offset = 0; offset < source.length(); ) {
                    int codePoint = source.codePointAt(offset);
                    node = node.children.computeIfAbsent(codePoint, ignored -> new TrieNode());
                    offset += Character.charCount(codePoint);
                }
                node.target = target;
            });
        }

        private void loadCharacters(String resourceName) {
            forEachEntry(resourceName, (source, target) -> {
                if (source.codePointCount(0, source.length()) == 1) {
                    characters.put(source.codePointAt(0), target);
                }
            });
        }

        private void forEachEntry(String resourceName, EntryConsumer consumer) {
            try (InputStream inputStream = SubtitleChineseConverter.class.getResourceAsStream(RESOURCE_ROOT + resourceName)) {
                if (inputStream == null) {
                    throw new IllegalStateException("未找到字幕转换词典资源: " + resourceName);
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank() || line.startsWith("#")) {
                            continue;
                        }
                        int delimiter = line.indexOf('\t');
                        if (delimiter <= 0 || delimiter >= line.length() - 1) {
                            continue;
                        }
                        String source = line.substring(0, delimiter);
                        String target = line.substring(delimiter + 1).split(" ", 2)[0];
                        if (!target.isEmpty()) {
                            consumer.accept(source, target);
                        }
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("读取字幕转换词典失败: " + resourceName, exception);
            }
        }

        private String convert(String source) {
            StringBuilder result = new StringBuilder(source.length());
            for (int offset = 0; offset < source.length(); ) {
                TrieNode node = phraseRoot;
                int cursor = offset;
                String matchedTarget = null;
                int matchedEnd = offset;

                while (cursor < source.length()) {
                    int codePoint = source.codePointAt(cursor);
                    node = node.children.get(codePoint);
                    if (node == null) {
                        break;
                    }
                    cursor += Character.charCount(codePoint);
                    if (node.target != null) {
                        matchedTarget = node.target;
                        matchedEnd = cursor;
                    }
                }

                if (matchedTarget != null) {
                    result.append(matchedTarget);
                    offset = matchedEnd;
                    continue;
                }

                int codePoint = source.codePointAt(offset);
                result.append(characters.getOrDefault(codePoint,
                        new String(Character.toChars(codePoint))));
                offset += Character.charCount(codePoint);
            }
            return result.toString();
        }
    }

    private interface EntryConsumer {
        void accept(String source, String target);
    }

    private static final class TrieNode {
        private final Map<Integer, TrieNode> children = new HashMap<>();
        private String target;
    }
}
