package cn.tealc.asmr.utils.comparator;

import cn.tealc.asmr.model.AsmrFile;
import java.util.Comparator;


/**
 * {@link AsmrFile} 排序器：目录排在文件之前，同组内按文件名的自然顺序比较。
 * <p>
 * 自然顺序会把文件名中的连续数字按数值大小比较（例如 {@code 2} 排在 {@code 10} 之前），
 * 并在比较前将全角字符归一为半角（见 {@link #convert}）。
 *
 * @author yiqiu
 */
public class AsmrFileComparator implements Comparator<AsmrFile> {
    /**
     * 先判断目录归属（目录优先），其余情况委托 {@link #naturalOrder} 比较两者文件名。
     *
     * @param o1 待比较文件
     * @param o2 待比较文件
     * @return 负数表示 {@code o1} 应排在 {@code o2} 之前，正数相反，{@code 0} 表示顺序相同
     */
    @Override
    public int compare(AsmrFile o1, AsmrFile o2) {
         if (o1.isDir() && !o2.isDir()) {
            return -1;
        }else if (o2.isDir() && !o1.isDir()) {
            return 1;
        }else {
            return naturalOrder(o1.getFilename(), o2.getFilename());
        }

    }
    /**
     * 自然序比较：连续数字段按数值大小比较，非数字字符按 Unicode 码点逐个比较；逐段相等时较短字符串排前。
     *
     * @param s1 已归一化的文件名
     * @param s2 已归一化的文件名
     * @return 比较结果，规则同 {@link Comparator#compare}
     */
    private int naturalOrder(String s1, String s2) {
        s1 = convert(s1);
        s2 = convert(s2);
        int i1 = 0, i2 = 0;
        int len1 = s1.length(), len2 = s2.length();

        while (i1 < len1 && i2 < len2) {
            char c1 = s1.charAt(i1);
            char c2 = s2.charAt(i2);

            if (Character.isDigit(c1) && Character.isDigit(c2)) {
                // 比较数字部分
                int num1 = 0, num2 = 0;
                while (i1 < len1 && Character.isDigit(s1.charAt(i1))) {
                    num1 = num1 * 10 + (s1.charAt(i1) - '0');
                    i1++;
                }
                while (i2 < len2 && Character.isDigit(s2.charAt(i2))) {
                    num2 = num2 * 10 + (s2.charAt(i2) - '0');
                    i2++;
                }
                if (num1 != num2) {
                    return Integer.compare(num1, num2);
                }
            } else {
                // 比较字符部分
                if (c1 != c2) {
                    return Character.compare(c1, c2);
                }
                i1++;
                i2++;
            }
        }

        return Integer.compare(len1, len2);
    }

    /**
     * 将全角 ASCII 字符（U+FF01–U+FF5E）归一为对应半角字符，其余字符保持不变。
     *
     * @param fullwidth 原始字符串
     * @return 归一后的字符串（不处理全角空格 U+3000）
     */
    public String convert(String fullwidth) {
        StringBuilder halfwidth = new StringBuilder();
        for (char c : fullwidth.toCharArray()) {
            // 处理全角字符（ASCII范围内）
            if (c >= 0xFF01 && c <= 0xFF5E) {
                halfwidth.append((char) (c - 0xFEE0));
            } else {
                halfwidth.append(c); // 其他字符不变
            }
        }
        return halfwidth.toString();
    }

    private boolean isNumeric(String str) {
        return str.matches("\\d+");
    }
}