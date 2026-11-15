package a;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * GBK 与 UTF-8 之间字节级转换的手工验证脚本，靠 {@code main} 直接运行，没有断言。
 * <p>
 * 演示把 GBK 字节先按 ISO-8859-1 读出、再按 UTF-8 还原的乱码修复链路，
 * 以及手工按 UTF-8 三字节规则拼装字符。仅用于排查编码问题，不参与构建或测试。
 *
 * @author Leck
 */
public class gbkToUtf {
    public static void main(String[] args) throws UnsupportedEncodingException {
        String gbk="我来了";
        String gbk1 = new String(gbk.getBytes(), "GBK");
        System.out.println(gbk1);
        String gbk2 = new String(gbk1.getBytes("GBK"), "ISO-8859-1");
        System.out.println(gbk2);
        System.out.println(new String(gbk2.getBytes("ISO-8859-1"),"UTF-8"));
        for (byte b : gbk1.getBytes()) {
            System.out.print(b + " ");
        }
        System.out.println();
        for (byte b : gbk2.getBytes()) {
            System.out.print(b + " ");
        }
    }

    /**
     * 手工按 UTF-8 三字节格式拼装字符并打印，用于对照 JDK 的编码结果。
     * <p>
     * 只假设字符位于 BMP（每个 char 展开成 3 字节），因此不能处理代理对；
     * 当前 {@code main} 未调用它，需要时自行接上。
     */
    public static void gbk2Utf(String gbk) throws UnsupportedEncodingException {

        char[] c = gbk.toCharArray();
        byte[] fullByte = new byte[3*c.length];
        for (int i=0; i<c.length; i++) {
            String binary = Integer.toBinaryString(c[i]);
            StringBuffer sb = new StringBuffer();
            int len = 16 - binary.length();
            //前面补零
            for(int j=0; j<len; j++){
                sb.append("0");
            }
            sb.append(binary);
            //增加位，达到到24位3个字节
            sb.insert(0, "1110");
            sb.insert(8, "10");
            sb.insert(16, "10");
            fullByte[i*3] = Integer.valueOf(sb.substring(0, 8), 2).byteValue();//二进制字符串创建整型
            fullByte[i*3+1] = Integer.valueOf(sb.substring(8, 16), 2).byteValue();
            fullByte[i*3+2] = Integer.valueOf(sb.substring(16, 24), 2).byteValue();
        }
        //模拟UTF-8编码的网站显示
        System.out.println(new String(fullByte,"UTF-8"));
    }
}