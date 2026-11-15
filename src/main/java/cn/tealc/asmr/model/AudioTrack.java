package cn.tealc.asmr.model;

import cn.tealc.asmr.model.collection.ItemData;
import cn.tealc.asmr.model.collection.ItemDataEx;
import cn.tealc.asmr.utils.URLUtils;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

import java.io.File;
import java.nio.file.Paths;

/**
 * 播放器与播放列表中的一条音轨（一个音声章节 / 音频文件）。
 * <p>
 * 核心字段使用 JavaFX Property，供播放列表与详情界面直接绑定；{@code online} 表示所属
 * 作品来自网络存储源（{@code storageType != 0}），{@code isStrm} 表示曲目指向
 * {@code .strm} 快捷文件而非真实音频。判等以 {@code url} 为准。
 *
 * @author Leck
 */
public class AudioTrack {
    private final SimpleIntegerProperty id = new SimpleIntegerProperty();
    private final SimpleStringProperty name = new SimpleStringProperty();//歌曲名称
    private final SimpleStringProperty hideName = new SimpleStringProperty();
    private final SimpleStringProperty artist = new SimpleStringProperty();//演唱者
    private final SimpleStringProperty album = new SimpleStringProperty();//专辑
    private final SimpleIntegerProperty duration = new SimpleIntegerProperty();//时长
    private final SimpleStringProperty size = new SimpleStringProperty();//大小
    private final SimpleStringProperty url = new SimpleStringProperty();//路径
    private final SimpleIntegerProperty star = new SimpleIntegerProperty();

    private AsmrWork work;
    private boolean online;
    private boolean hasLrc;
    private boolean isStrm;




    public AudioTrack(AsmrWork work, AsmrFile file) {
        setName(file.getFilename());
        setUrl(file.getFilepath());
        setArtist(work.getVas());
        setWork(work);
        setOnline(work.getStorageType() != 0);
        // 根据 AsmrFile.type 判断是否为 strm 文件，仅设置标记，不读取内容
        setStrm("strm".equalsIgnoreCase(file.getType()));
    }
    public AudioTrack(ItemDataEx item) {
        String path = item.getWork().getFilepath() + File.separator + item.getFilepath();
        setName(item.getName());
        setUrl(path);
        setArtist(item.getWork().getVas());
        setWork(item.getWork());
        setOnline(item.getWork().getStorageType() != 0);
    }



    public AudioTrack(String name, String url,boolean online,AsmrWork work) {
        setName(name);
        setHideName(name);
        setUrl(url);
        setOnline(online);
        setWork(work);
    }


    public AudioTrack(String name, String artist, String album, int duration, String url) {
        setName(name);
        setArtist(artist);
        setAlbum(album);
        setDuration(duration);
        setUrl(url);
        setOnline(false);
    }





    public String getHideName() {
        return hideName.get();
    }

    public String getUri(){
        return URLUtils.getPathToURI(getUrl());
    }

    public SimpleStringProperty hideNameProperty() {
        return hideName;
    }

    public void setHideName(String hideName) {
        this.hideName.set(hideName);
    }

    public int getId() {
        return id.get();
    }

    public SimpleIntegerProperty idProperty() {
        return id;
    }

    public void setId(int id) {
        this.id.set(id);
    }

    public String getName() {
        return name.get();
    }

    public SimpleStringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public String getArtist() {
        return artist.get();
    }

    public SimpleStringProperty artistProperty() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist.set(artist);
    }

    public String getAlbum() {
        return album.get();
    }

    public SimpleStringProperty albumProperty() {
        return album;
    }

    public void setAlbum(String album) {
        this.album.set(album);
    }

    public double getDuration() {
        return duration.get();
    }

    public SimpleIntegerProperty durationProperty() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration.set(duration);
    }

    public String getSize() {
        if (size!=null){
            return size.get();
        }else{
            return null;
        }
    }

    public SimpleStringProperty sizeProperty() {
        return size;
    }

    public void setSize(String size) {
        this.size.set(size);
    }

    public String getUrl() {
        return url.get();
    }

    public SimpleStringProperty urlProperty() {
        return url;
    }

    public void setUrl(String url) {
        this.url.set(url);
    }

    public int getStar() {
        return star.get();
    }

    public SimpleIntegerProperty starProperty() {
        return star;
    }

    public void setStar(int star) {
        this.star.set(star);
    }


    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public boolean isHasLrc() {
        return hasLrc;
    }

    public void setHasLrc(boolean hasLrc) {
        this.hasLrc = hasLrc;
    }

    public boolean isStrm() {
        return isStrm;
    }

    public void setStrm(boolean strm) {
        isStrm = strm;
    }

    public AsmrWork getWork() {
        return work;
    }

    public void setWork(AsmrWork work) {
        this.work = work;
    }

    @Override
    public String toString() {
        return "AudioTrack{" +
                "id=" + id +
                ", name=" + name +
                ", artist=" + artist +
                ", album=" + album +
                ", duration=" + duration +
                ", size=" + size +
                ", url=" + url +
                ", star=" + star +
                '}';
    }


    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AudioTrack){
            AudioTrack music = (AudioTrack) obj;
            if (music.getUrl()!=null && !music.getUrl().equals("")){
                if (getUrl().equals(music.getUrl())){
                    return true;
                }
            }
        }
        return false;
    }
}
