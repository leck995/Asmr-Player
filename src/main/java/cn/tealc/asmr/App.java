package cn.tealc.asmr;


import cn.tealc.asmr.utils.AppPaths;
import javafx.application.Application;

/**
 * 应用打包后的启动入口（fat jar 的 Main-Class 即本类）。
 * <p>
 * 启动顺序：先调用 {@code AppPaths.init()} 初始化数据/日志目录并写入系统属性
 * （必须早于任何 logger 初始化），再通过 {@link Application#launch(Class, String[])}
 * 启动 JavaFX 运行时，并把生命周期交给 {@link Main}——后续的数据库初始化、DI 绑定与
 * 主窗口构建都在 {@link Main#start(javafx.stage.Stage)} 中完成。
 * {@link Main} 的静态块同样会调用 {@code AppPaths.init()}（重复调用无副作用），
 * 因此直接运行 {@link Main#main(String[])} 也能正常启动。
 *
 * @author Leck
 */
public class App  {

    public static void main(String[] args) {
        AppPaths.init();
        Application.launch(Main.class,args);
    }


}