package cn.tealc.asmr.task;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.config.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.concurrent.Task;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * 检查 GitHub Releases 上的最新版本。
 * <p>
 * 拉取 {@code leck995/Asmr-Player} 最新 Release 的 {@code tag_name}，与本地
 * {@link Config#version} 去掉小数点后按数值比较；发现更新且用户未通过
 * {@code ignoreVersion} 忽略该版本时，发布 {@code MESSAGE} 主题提示消息
 * （键见 {@link cn.tealc.asmr.config.NotificationKey#MESSAGE}）。
 * {@link #call()} 返回是否存在新版本；网络读取失败时发布错误消息并抛出异常。
 *
 * @author Leck
 */
public class CheckVersionTask extends Task<Boolean> {
    private static final String NET_URL="https://api.github.com/repos/leck995/Asmr-Player/releases/latest";
    private static final String TIP="发现新版本：%s,可在设置中获取更新详细信息";
    private static final String TIP_ERROR="检查版本更新失败，请检查网络状况";
    @Override
    protected Boolean call() throws Exception {
        ObjectMapper objectMapper=new ObjectMapper();
        URL url= null;
        try {
            url = new URL(NET_URL);
            Map<String, String> versionMap = objectMapper.readValue(url, Map.class);
            String version=versionMap.get("tag_name");
            double net = Double.parseDouble(version.replace(".",""));
            if (Config.setting.ignoreVersion.get() != null){
                double ignore = Double.parseDouble(Config.setting.ignoreVersion.get().replace(".",""));
                if (net == ignore){
                    return false;
                }
            }

            double now = Double.parseDouble(Config.version.replace(".",""));
            if (now < net){
                Thread.sleep(2000);
                EventBus.publish("MESSAGE",new MessageInfo(MessageType.INFO,String.format(TIP,version)));
                return true;
            }
        } catch (IOException e) {
            EventBus.publish("MESSAGE",new MessageInfo(MessageType.ERROR,TIP_ERROR));
            throw new RuntimeException(e);
        }
        return false;
    }
}