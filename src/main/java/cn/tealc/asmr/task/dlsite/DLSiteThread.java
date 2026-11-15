package cn.tealc.asmr.task.dlsite;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.dlsite.*;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.model.ResponseBody;
import cn.tealc.asmr.utils.CoverUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * 抓取并解析单个作品的 DLSite 元数据。
 * <p>
 * 请求 {@code maniax/api/=/product.json} 接口获取作品 JSON，解析为 {@link DLSiteWork} 后
 * 下载封面（保存到作品目录或封面缓存目录，由 {@code saveCoverToAimFolderModel} 决定），
 * 再把站点数据填充进传入的 {@link AsmrWork}，以 {@link ResponseBody} 返回。
 * 同步基底网址取自 {@code Config.setting.sourcesUrl}，未配置为 https 地址时构造即抛出
 * {@link IllegalStateException}。
 * <p>
 * 每次 {@link #call()} 结尾固定休眠 50 毫秒，用于对 DLSite 接口限速。
 *
 * @author Leck
 */
public class DLSiteThread implements Callable<ResponseBody<AsmrWork>> {
    private static final Logger logger = LoggerFactory.getLogger(DLSiteThread.class);
    private static final String URL_TEMPLATE = "%s/maniax/api/=/product.json?locale=zh_CN&workno=%s";

    private String workId;
    private String path;
    private String url;
    private boolean over;
    private boolean saveToAimFolder;
    private AsmrWork asmrWork;
    private HttpClient client;

    /**
     * 使用配置中的封面重下载开关构造同步线程。
     */
    public DLSiteThread(HttpClient client, AsmrWork asmrWork) {
        this(client, asmrWork, Config.setting.syncImageReDownModel.get());
    }

    /**
     * @param client   复用的 HTTP 客户端（可能带有代理配置）
     * @param asmrWork 待同步的作品记录，封面保存路径与工作号从中读取
     * @param over     是否强制重新下载封面；false 时作品已有封面则跳过下载
     */
    public DLSiteThread(HttpClient client, AsmrWork asmrWork, boolean over) {
        this.client = client;
        this.asmrWork = asmrWork;
        this.path = asmrWork.getFilepath();
        this.workId = asmrWork.getWorkId();
        this.over = over;
        this.saveToAimFolder = Config.setting.saveCoverToAimFolderModel.get();
        String sourcesUrl = Config.setting.sourcesUrl.get();
        if (sourcesUrl == null || !sourcesUrl.startsWith("https://")) {
            throw new IllegalStateException("请先正确设置同步网址");
        }
        if (sourcesUrl.endsWith("/")) {
            url = sourcesUrl.substring(0, sourcesUrl.length() - 1);
        } else {
            url = sourcesUrl;
        }
    }

    /**
     * 完整同步流程：请求作品 JSON → 按需二次请求探测简体中文版标题 → 下载封面 → 格式转换。
     *
     * @return SUCCESS 时携带填充完成的 {@link AsmrWork}；请求或解析失败时返回
     *         ERROR 并携带错误描述，不抛异常
     */
    @Override
    public ResponseBody<AsmrWork> call() {
        ResponseBody<AsmrWork> responseBody = new ResponseBody<>();
        String json = request(workId);
        if (json != null) {
            DLSiteWork dlSiteWork = parse(json);
            if (dlSiteWork != null) {
                //寻找含有中文的作品
                if (Config.setting.isUserChineseTitle()) {
                    DLLanguage cn = dlSiteWork.getCN();
                    if (cn != null) {
                        if (!cn.getWorkno().equalsIgnoreCase(dlSiteWork.getWorkno())) { //当前不是简体时，注意，这里即使源作品是繁体也会判断为不是简体而进行下载
                            String jsonWithLanguage = request(workId);
                            if (jsonWithLanguage != null) {
                                DLSiteWork dlSiteWork2 = parse(jsonWithLanguage);
                                if (dlSiteWork2 != null) {
                                    String workName = dlSiteWork2.getWork_name().replace("【繁体中文版】", "").replace("【簡体中文版】", "");
                                    if (!workName.equals(dlSiteWork.getWork_name())) {//部分作品占着茅坑不拉屎，对此类行为导致的虚假作品进行筛选
                                        if (dlSiteWork2.getWork_name().contains("简体中文版")) {
                                            dlSiteWork.setTitleCN(dlSiteWork2.getWork_name());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (saveToAimFolder) {
                    saveCover(1, dlSiteWork.getImage_thumb(), path, over);
                    saveCover(2, dlSiteWork.getImage_main().getUrl(), path, over);
                } else {
                    saveThumbCover(dlSiteWork.getImage_thumb(), dlSiteWork.getWorkno(), over);
                    saveMainCover(dlSiteWork.getImage_main().getUrl(), dlSiteWork.getWorkno(), over);
                }
                AsmrWork work = matching(dlSiteWork, asmrWork);
                responseBody.setData(work);
                responseBody.setCode(ResponseBody.StatusCode.SUCCESS);
            } else {
                String s = String.format("失败：无法获取到 %s 的作品信息", asmrWork.getWorkId());
                logger.info(s);
                responseBody.setMsg(s);
                responseBody.setCode(ResponseBody.StatusCode.ERROR);
            }
        } else {
            String s = String.format("失败: %s 的作品请求同步数据失败", asmrWork.getWorkId());
            logger.info(s);
            responseBody.setMsg(s);
            responseBody.setCode(ResponseBody.StatusCode.ERROR);
        }

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }

        return responseBody;
    }


    /**
     * 将 DLSite 返回的数据格式转换成内置的 {@link AsmrWork} 格式。
     * <p>
     * 直接在传入的作品对象上填充语言版本父子关系、原版工作号、中文标题（仅本地缺失时回填）、
     * 社团、声优、分类与封面地址等元数据。
     *
     * @param dlSiteWork 解析出的 DLSite 作品数据
     * @param asmrWork   待填充的作品对象
     * @return 与 {@code asmrWork} 相同的对象引用
     */
    private AsmrWork matching(DLSiteWork dlSiteWork, AsmrWork asmrWork) {
        // 直接在传入的作品对象上填充元数据，不需要重新从 DB 加载
        AsmrWork sources = asmrWork;

        logger.info("开始转换 {} 的作品信息格式，", sources.getWorkId());
        List<DLLanguage> languageEditions = dlSiteWork.getLanguage_editions();
        if (languageEditions != null) {
            DLTranslationInfo translationInfo = dlSiteWork.getTranslation_info();
            if (!translationInfo.isIs_original() && !translationInfo.isIs_parent()) {
                String parentWorkno = translationInfo.getParent_workno();
                if (parentWorkno != null) {
                    for (DLLanguage languageEdition : dlSiteWork.getLanguage_editions()) {
                        if (languageEdition.getWorkno().equalsIgnoreCase(parentWorkno)) {
                            languageEdition.setChildren(dlSiteWork.getWorkno());
                        }
                    }
                }
            }
            if (translationInfo.isIs_parent()) {
                if (translationInfo.getChild_worknos() != null) {
                    String childNo = translationInfo.getChild_worknos().stream().collect(Collectors.joining(","));
                    for (DLLanguage languageEdition : dlSiteWork.getLanguage_editions()) {
                        if (languageEdition.getWorkno().equalsIgnoreCase(dlSiteWork.getWorkno())) {
                            languageEdition.setChildren(childNo);
                        }
                    }
                }
            }
            if (translationInfo.isIs_original()) {
                sources.setOriginWorkId(sources.getWorkId());
            } else {
                if (!translationInfo.isIs_original() && translationInfo.getOriginal_workno() != null) {
                    sources.setOriginWorkId(translationInfo.getOriginal_workno());
                }
            }
            String collect = languageEditions.stream().map(DLLanguage::getSaveText).collect(Collectors.joining("*"));
            sources.setRelatedWorkIds(collect);
        }

        if (dlSiteWork.getTitleCN() != null) {
            logger.debug("{}: 检测到有中文", sources.getWorkId());
            if (sources.getTitleCN() == null || sources.getTitleCN().isEmpty()) {//只有当现有中文标题为null或空时才替换
                sources.setTitleCN(dlSiteWork.getTitleCN());
                logger.debug("{}: 已设置中文标题", sources.getWorkId());
            } else {
                logger.debug("{}: 已设置中文标题", sources.getWorkId());
            }
        }
        sources.setSources("dlsite");
        sources.setTitle(dlSiteWork.getWork_name());
        sources.setRelease(dlSiteWork.getRegist_date());
        sources.setNsfw(dlSiteWork.isNsfw());
        sources.setCircle(dlSiteWork.getMaker_name());

        Optional<String> categroy = dlSiteWork.getGenres().stream()
                .map(DLGenre::getName)
                .reduce((s1, s2) -> s1 + "," + s2);
        categroy.ifPresent(sources::setCategories);

        if (dlSiteWork.getCreaters().getVoice_by() != null) {
            Optional<String> cv = dlSiteWork.getCreaters().getVoice_by().stream()
                    .map(DLActor::getName)
                    .reduce((s1, s2) -> s1 + "," + s2);
            cv.ifPresent(sources::setVas);
        }

        sources.setMainCoverUrl(dlSiteWork.getImage_main().getUrl());
        sources.setThumbnailCoverUrl(dlSiteWork.getImage_thum().getUrl());
        sources.setSamCoverUrl(dlSiteWork.getImage_thum_mini().getUrl());
        logger.debug("DLSiteWork to AsmrWork 转换为内置格式成功");
        return sources;
    }


    /**
     * 向 DLSite 请求指定作品号的作品信息 JSON。
     * <p>
     * 响应中 {@code creaters} 字段缺失时，把空数组替换成空对象再返回，保证反序列化成功。
     *
     * @param workId DLSite 作品号
     * @return 响应体字符串；状态码非 200 或网络异常时返回 {@code null}
     */
    public String request(String workId) {
        URI uri = null;
        try {
            uri = URI.create(String.format(URL_TEMPLATE, this.url, workId));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0")
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                String row = response.body().replace("\"creaters\":[]", "\"creaters\":{}"); //当creaters不存在的异常处理
                return row;
            } else {
                logger.error("同步请求连接失败，状态码:{}", response.statusCode());
            }
        } catch (IOException e) {
            logger.error("同步错误:{}", uri, e);
        } catch (InterruptedException e) {
            logger.error("连接出错:{}", uri, e);
        }
        return null;
    }

    /**
     * 将作品 JSON 解析为 {@link DLSiteWork}，并逐节点手动提取 {@code language_editions}
     * 各语言版本（workno / label / 显示顺序）填入解析结果。
     *
     * @param json {@link #request(String)} 返回的响应体
     * @return 数组首元素作品数据；空数组或解析失败返回 {@code null}
     */
    public DLSiteWork parse(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            List<DLSiteWork> dlSiteWorks = mapper.readValue(json, new TypeReference<>() {
            });
            if (!dlSiteWorks.isEmpty()) {
                DLSiteWork dlSiteWork = dlSiteWorks.get(0);
                JsonNode jsonNode = mapper.readTree(json);
                JsonNode languageEditions = jsonNode.get(0).get("language_editions");
                if (!languageEditions.isEmpty()) {
                    List<DLLanguage> languages = new ArrayList<>();
                    DLLanguage dlLanguage;
                    for (JsonNode node : languageEditions) {
                        dlLanguage = new DLLanguage();
                        dlLanguage.setWorkno(node.get("workno").asText());
                        dlLanguage.setLabel(node.get("label").asText());
                        dlLanguage.setDisplay_order(node.get("display_order").asInt());
                        dlLanguage.setLabel(node.get("label").asText());
                        languages.add(dlLanguage);
                    }
                    dlSiteWork.setLanguage_editions(languages);
                } else {
                    logger.debug("无其他语言翻译");
                }
                return dlSiteWorks.getFirst();
            } else {
                logger.error("JSON解析出现问题:解析为null");
                return null;
            }
        } catch (IOException e) {
            logger.error("JSON解析出现问题", e);
        }
        return null;
    }


    private void saveCover(int type, String url, String path, boolean over) {
        File dir = new File(path);
        if (!over) {
            File[] files = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    if (name.contains("cover-thumb") || name.contains("cover-main"))
                        return true;
                    return false;
                }
            });
            if (files.length >= 2) {
                return;
            }
        }
        File file = null;
        if (type == 1) {
            if (url.contains(".jpg")) {
                file = new File(path + File.separator + "cover-thumb.jpg");
            } else if (url.contains(".png")) {
                file = new File(path + File.separator + "cover-thumb.png");
            } else if (url.contains(".jpeg")) {
                file = new File(path + File.separator + "cover-thumb.jpeg");
            } else if (url.contains(".gif")) {
                logger.warn("{}:封面不存在", workId);
            } else {
                logger.warn("{}图片格式未识别", workId);
            }
        }
        if (type == 2) {
            if (url.contains(".jpg")) {
                file = new File(path + File.separator + "cover-main.jpg");
            } else if (url.contains(".png")) {
                file = new File(path + File.separator + "cover-main.png");
            } else if (url.contains(".jpeg")) {
                file = new File(path + File.separator + "cover-main.jpeg");
            } else if (url.contains(".gif")) {
                logger.warn("{}:封面不存在", workId);
            } else {
                logger.warn("{}图片格式未识别", workId);
            }
        }
        downloadFile(url, file);
    }

    private void saveMainCover(String url, String workId, boolean over) {
        File mainDir = CoverUtils.getCacheDirectory("main");
        if (!mainDir.exists()) mainDir.mkdirs();
        if (!over) {
            boolean exist = checkImageExist(mainDir, workId);
            if (exist) {
                return;
            }
        }

        File file = null;
        if (url.contains(".jpg")) {
            file = new File(mainDir, workId + ".jpg");
        } else if (url.contains(".png")) {
            file = new File(mainDir, workId + ".png");
        } else if (url.contains(".jpeg")) {
            file = new File(mainDir, workId + ".jpeg");
        } else if (url.contains(".gif")) {
            logger.warn("{}:封面不存在", workId);
        } else {
            logger.warn("{}:图片格式未识别", workId);
        }
        downloadFile(url, file);
    }


    private void saveThumbCover(String url, String workId, boolean over) {
        File mainDir = CoverUtils.getCacheDirectory("thumb");
        if (!mainDir.exists()) mainDir.mkdirs();
        if (!over) {
            boolean exist = checkImageExist(mainDir, workId);
            if (exist) {
                return;
            }
        }
        File file = null;
        if (url.contains(".jpg")) {
            file = new File(mainDir, workId + ".jpg");
        } else if (url.contains(".png")) {
            file = new File(mainDir, workId + ".png");
        } else if (url.contains(".jpeg")) {
            file = new File(mainDir, workId + ".jpeg");
        } else if (url.contains(".gif")) {
            logger.warn("{}:封面不存在", workId);
        } else {
            logger.warn("{}:图片格式未识别", workId);
        }
        downloadFile(url, file);

    }


    private boolean checkImageExist(File dir, String workId) {
        File file1 = new File(dir, workId + ".jpg");
        if (file1.exists()) {
            return true;
        } else {
            File file2 = new File(dir, workId + ".jpeg");
            if (file2.exists()) {
                return true;
            } else {
                File file3 = new File(dir, workId + ".png");
                if (file3.exists())
                    return true;
            }
        }
        return false;
    }


    /**
     * 下载 URL 内容到指定文件；{@code saveFile} 为 {@code null}（图片格式未识别）时直接跳过。
     */
    private void downloadFile(String fileUrl, File saveFile) {
        if (saveFile == null)
            return;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0")
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            File dir = saveFile.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            Files.copy(response.body(), saveFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.debug("文件已下载并保存到:{}", saveFile.getPath());
        } catch (IOException | InterruptedException e) {
            logger.error("文件 {} 下载失败，目标文件 {}", fileUrl, saveFile.getPath(), e);
        }
    }
}
