package cn.tealc.asmr.mvvm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ViewModel 注入注解。
 * <p>
 * 标注在 View 类的字段上。{@link FluentViewLoader} 加载 View 时，
 * 通过反射将对应的 ViewModel 实例注入到所有标注此注解的字段。
 *
 * @author Leck
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectViewModel {
}
