package cn.tealc.asmr.di;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段注入注解。
 * <p>
 * 标注在类的字段上，由 {@link DI#inject(Object)} 反射扫描后，
 * 从容器中获取对应类型的单例并注入。典型用法：
 * <pre>{@code
 * public class WorkQueryService {
 *     @Inject private WorkDao workDao;
 * }
 * }</pre>
 *
 * @author Leck
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Inject {
}
