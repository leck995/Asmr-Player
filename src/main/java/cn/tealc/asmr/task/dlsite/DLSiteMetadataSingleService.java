package cn.tealc.asmr.task.dlsite;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.service.DictionaryService;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.model.ResponseBody;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;

/**
 * 单作品 DLSite 元数据同步任务。
 * <p>
 * 在后台线程执行 {@link DLSiteThread} 抓取作品信息，成功后把作品出现的分类、
 * 声优、社团写入字典库并更新作品记录；失败时仅更新任务消息并返回 {@code null}。
 *
 * @author Leck
 */
public class DLSiteMetadataSingleService extends Task<AsmrWork> {
    private static final Logger logger = LoggerFactory.getLogger(DLSiteMetadataSingleService.class);
    private AsmrWork work;
    private HttpClient client;;

    /**
     * 按 {@code Config.setting} 中的代理设置构建 HTTP 客户端：开启代理且地址、端口
     * 均非空时使用代理，否则直连。
     */
    public DLSiteMetadataSingleService(AsmrWork work) {
        this.work = work;
        String host = Config.setting.proxyHost.get();
        String port = Config.setting.proxyPort.get();

        if (Config.setting.proxyModel.get() && host != null && port != null) {
            client = HttpClient.newBuilder().proxy(ProxySelector.of(new InetSocketAddress(host, Integer.parseInt(port)))).build();
        }else {
            client = HttpClient.newHttpClient();
        }
    }

    public void setWork(AsmrWork work) {
        this.work = work;
    }


    /**
     * 抓取并回写单个作品的元数据：同步成功时把逗号分隔的分类 / 声优 / 社团逐一登记进
     * 字典库，再更新作品记录；同步失败时返回 {@code null}。
     *
     * @return 同步成功后的作品对象，失败为 {@code null}
     */
    @Override
    protected AsmrWork call() {
        WorkService workService = DI.get(WorkService.class);
        DictionaryService dictService = DI.get(DictionaryService.class);
        DLSiteThread dlSiteThread = new DLSiteThread(client,work);
        ResponseBody<AsmrWork> responseBody = dlSiteThread.call();
        AsmrWork sources = null;

        if (responseBody.getCode() == ResponseBody.StatusCode.SUCCESS) {
            sources = responseBody.getData();
            if (sources != null) {
                logger.info("{}:成功获取作品信息", sources.getWorkId());
                if (sources.getCategories() != null) {
                    for (String s : sources.getCategories().split(",")) {
                        dictService.addCategory(s);
                    }
                }
                if (sources.getVas() != null) {
                    for (String s : sources.getVas().split(",")) {
                        dictService.addVa(s);
                    }
                }
                if (sources.getCircle() != null) {
                    dictService.addCircle(sources.getCircle());
                }
                workService.updateWork(sources);
            } else {
                updateMessage(work.getTitle() + ":同步失敗");
                logger.info("失败：无法获取到 {} 的作品信息", sources.getWorkId());
            }
        }

        return sources;
    }


}
