package cn.tealc.asmr.ui.dialog;

import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.utils.CssLoader;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;

/**
 * 赞助弹窗：展示支持开发者的说明与收款二维码。
 * <p>
 * 入口位于主界面标题栏主题切换按钮右侧，用户无需进入设置页即可查看。
 *
 * @author Leck
 */
public final class SponsorDialog {

    /** 收款二维码图片。 */
    private static final String QR_IMAGE = "/cn/tealc/asmr/image/others/support.png";

    private SponsorDialog() {
    }

    /**
     * 展示赞助弹窗，必须在 JavaFX 线程调用。
     */
    public static void show() {
        PageHeader header = new PageHeader();
        header.setIcon(MaterialDesignC.COFFEE);
        header.setTitle("赞助");
        header.setSubtitle("觉得这个项目不错？欢迎请作者喝一杯咖啡");

        Label description = new Label(
                """
                未来道具10号是一款为爱发电的作品，无任何收费功能。
                如果本项目对您的学习有所帮助，且财力有余，欢迎支持作者一波。
                非常建议按以下格式留言：10号@[名称]:[想说的话]
                """);
        description.setWrapText(true);
        description.getStyleClass().add("sponsor-description");

        ImageView qrCode = new ImageView(new Image(
                SponsorDialog.class.getResource(QR_IMAGE).toExternalForm()));
        qrCode.setFitWidth(360);
        qrCode.setPreserveRatio(true);
        qrCode.setSmooth(true);
        qrCode.getStyleClass().add("sponsor-qr");

        Label hint = new Label("使用支付宝或微信扫码赞赏，感谢你的支持！");
        hint.getStyleClass().add("sponsor-hint");

        VBox body = new VBox(12, description, qrCode, hint);
        body.setAlignment(Pos.TOP_CENTER);
        body.getStyleClass().add("sponsor-body");

        Dialogs.show(DialogSpec.builder()
                .heading(header)
                .content(body)
                .width(460)
                .styleClass("sponsor-dialog")
                .stylesheet(CssLoader.getCss(CssLoader.sponsorDialog))
                .cancel("关闭")
                .build());
    }
}
