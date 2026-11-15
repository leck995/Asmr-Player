package cn.tealc.asmr.mvvm;

import cn.tealc.asmr.di.DI;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * View 加载器。
 * <p>
 * 提供链式 API 加载 {@link FXMLView} 和 {@link JavaView}：
 * <pre>{@code
 * // FXMLView，反射实例化无参 ViewModel
 * ViewTuple<SettingView, SettingViewModel> tuple =
 *     FluentViewLoader.fxmlView(SettingView.class).load();
 *
 * // FXMLView，主动传入 ViewModel（有参构造）
 * ViewTuple<UpdateInfoView, UpdateInfoViewModel> tuple =
 *     FluentViewLoader.fxmlView(UpdateInfoView.class)
 *         .viewModel(new UpdateInfoViewModel(work))
 *         .load();
 *
 * // JavaView（纯代码构建 UI）
 * ViewTuple<MainView, MainViewModel> tuple =
 *     FluentViewLoader.javaView(MainView.class).load();
 * }</pre>
 * <p>
 * 加载流程：
 * <ol>
 *   <li>FXMLView：用 FXMLLoader 加载同名 FXML，获取 root 和 controller</li>
 *   <li>JavaView：反射无参实例化 View 类（它继承 JavaFX Pane）</li>
 *   <li>创建/注入 ViewModel：扫描 {@link InjectViewModel} 字段并设值</li>
 *   <li>调用 initialize 方法（FXMLView 由 FXMLLoader 自动调用，JavaView 需手动调用）</li>
 *   <li>挂载 {@link SceneLifecycle} 钩子（监听 sceneProperty）</li>
 *   <li>返回 {@link ViewTuple}</li>
 * </ol>
 *
 * @author Leck
 */
public class FluentViewLoader {

    /**
     * 加载 FXMLView。
     *
     * @param viewClass View 类（实现 FXMLView，作为 FXML 的 controller）
     * @param <V>       View 类型，必须实现 FXMLView
     * @param <VM>      ViewModel 类型
     * @return 链式构建器
     */
    public static <V extends FXMLView<? extends VM>, VM extends ViewModel> FXMLViewStep<V, VM> fxmlView(Class<? extends V> viewClass) {
        return new FXMLViewStep<>(viewClass);
    }

    /**
     * 加载 JavaView。
     *
     * @param viewClass View 类（实现 JavaView，继承 JavaFX Pane）
     * @param <V>       View 类型，必须实现 JavaView
     * @param <VM>      ViewModel 类型
     * @return 链式构建器
     */
    public static <V extends JavaView<? extends VM>, VM extends ViewModel> JavaViewStep<V, VM> javaView(Class<? extends V> viewClass) {
        return new JavaViewStep<>(viewClass);
    }

    // ====================================================================
    // FXMLView 链式构建器
    // ====================================================================

    public static class FXMLViewStep<V extends FXMLView<? extends VM>, VM extends ViewModel> {
        private final Class<? extends V> viewClass;
        private VM viewModel;
        private ResourceBundle resourceBundle;

        FXMLViewStep(Class<? extends V> viewClass) {
            this.viewClass = viewClass;
        }

        /**
         * 主动传入 ViewModel 实例。不调用则反射无参实例化。
         */
        public FXMLViewStep<V, VM> viewModel(VM viewModel) {
            this.viewModel = viewModel;
            return this;
        }

        /**
         * 设置 ResourceBundle。
         */
        public FXMLViewStep<V, VM> resourceBundle(ResourceBundle resourceBundle) {
            this.resourceBundle = resourceBundle;
            return this;
        }

        @SuppressWarnings("unchecked")
        public ViewTuple<V, VM> load() {
            try {
                // 1. 定位 FXML 文件（约定：与 View 类同包，文件名 = 简单类名.fxml）
                String simplePath = viewClass.getPackageName().replace('.', '/') + "/" + viewClass.getSimpleName() + ".fxml";
                URL fxmlUrl = viewClass.getResource("/" + simplePath);
                if (fxmlUrl == null) {
                    throw new RuntimeException("Cannot find FXML file for " + viewClass.getName() +
                            ", tried: " + simplePath);
                }

                // 2. 预创建 ViewModel（需在 FXMLLoader.load 之前，确保注入到 initialize 调用之前）
                if (viewModel == null) {
                    Class<? extends ViewModel> vmClass = resolveViewModelClass(viewClass);
                    if (vmClass != null) {
                        viewModel = (VM) vmClass.getDeclaredConstructor().newInstance();
                    }
                }

                // 3. 加载 FXML，controllerFactory 在创建 controller 后立即注入 ViewModel
                final VM vmToInject = viewModel;
                FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl, resourceBundle);
                fxmlLoader.setControllerFactory(param -> {
                    try {
                        Object controller = param.getDeclaredConstructor().newInstance();
                        if (vmToInject != null) {
                            injectViewModel(controller, vmToInject);
                            DI.inject(vmToInject);
                        }
                        return controller;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create controller: " + param, e);
                    }
                });

                Parent root = fxmlLoader.load();
                V view = (V) fxmlLoader.getController();

                // 4. 挂生命周期钩子
                if (viewModel != null) {
                    attachLifecycleHook(viewModel, root);
                }

                return new ViewTuple<>(view, root, viewModel);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to load FXMLView: " + viewClass.getName(), e);
            }
        }
    }

    // ====================================================================
    // JavaView 链式构建器
    // ====================================================================

    public static class JavaViewStep<V extends JavaView<? extends VM>, VM extends ViewModel> {
        private final Class<? extends V> viewClass;
        private VM viewModel;

        JavaViewStep(Class<? extends V> viewClass) {
            this.viewClass = viewClass;
        }

        /**
         * 主动传入 ViewModel 实例。
         */
        public JavaViewStep<V, VM> viewModel(VM viewModel) {
            this.viewModel = viewModel;
            return this;
        }

        @SuppressWarnings("unchecked")
        public ViewTuple<V, VM> load() {
            try {
                // 1. JavaView 本身就是 Pane，直接实例化
                Constructor<? extends V> constructor = viewClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                V view = constructor.newInstance();

                // 2. 创建 ViewModel（如果未主动传入）
                if (viewModel == null) {
                    Class<? extends ViewModel> vmClass = resolveViewModelClass(view.getClass());
                    if (vmClass != null) {
                        viewModel = (VM) vmClass.getDeclaredConstructor().newInstance();
                    }
                }

                // 3. 注入 ViewModel + 挂生命周期钩子
                if (viewModel != null) {
                    injectViewModel(view, viewModel);
                    DI.inject(viewModel);
                    if (view instanceof Parent root) {
                        attachLifecycleHook(viewModel, root);
                    }
                }

                // 4. 调用 initialize（JavaView 不走 FXMLLoader，需手动调用）
                callInitialize(view);

                return new ViewTuple<>(view, (Parent) view, viewModel);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load JavaView: " + viewClass.getName(), e);
            }
        }
    }

    // ====================================================================
    // 内部工具方法
    // ====================================================================

    /**
     * 扫描 {@link InjectViewModel} 字段，反射注入 ViewModel 实例。
     */
    private static void injectViewModel(Object view, Object viewModel) throws IllegalAccessException {
        for (Field field : view.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(InjectViewModel.class)) {
                field.setAccessible(true);
                field.set(view, viewModel);
            }
        }
    }

    /**
     * 从 View 类的泛型接口签名解析 ViewModel 类型。
     * 例如 {@code FXMLView<SettingViewModel>} → {@code SettingViewModel.class}
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends ViewModel> resolveViewModelClass(Class<?> viewClass) {
        for (java.lang.reflect.Type type : viewClass.getGenericInterfaces()) {
            if (type instanceof java.lang.reflect.ParameterizedType pt) {
                java.lang.reflect.Type[] args = pt.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class<?> vmClass) {
                    if (ViewModel.class.isAssignableFrom(vmClass)) {
                        return (Class<? extends ViewModel>) vmClass;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 挂载 {@link SceneLifecycle} 钩子。
     * <p>
     * 监听 View 根节点的 {@code sceneProperty()}：
     * 节点加入 Scene Graph 时触发 onViewAdded，移出时触发 onViewRemoved。
     */
    private static void attachLifecycleHook(ViewModel viewModel, Parent viewRoot) {
        if (viewModel instanceof SceneLifecycle lifecycle) {
            viewRoot.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    lifecycle.onViewAdded();
                } else {
                    lifecycle.onViewRemoved();
                }
            });
        }
    }

    /**
     * 调用 View 的 initialize 方法。
     * <p>
     * 优先调用 {@link javafx.fxml.Initializable#initialize} 接口方法，
     * 若未实现则尝试反射调用无参 initialize() 方法。
     */
    private static void callInitialize(Object view) {
        if (view instanceof javafx.fxml.Initializable init) {
            init.initialize(null, null);
        } else {
            try {
                var method = view.getClass().getMethod("initialize");
                method.setAccessible(true);
                method.invoke(view);
            } catch (NoSuchMethodException e) {
                // 无 initialize 方法，正常情况
            } catch (Exception e) {
                throw new RuntimeException("Failed to call initialize on " + view.getClass().getName(), e);
            }
        }
    }
}
