# 未来道具10号
# 一、简介
  未来道具10号（暂定）是一款管理播放一体的播放器，主打音声，对某D音声网站进行适配，针对音声进行设计与实现。在传统播放器的基础上（包括播放，管理，桌面歌词等等），推出GalGame模式，探索并尝试将音声打造成小型GalGame,让音声的表现更加生动。
  软件使用JavaFX进行开发，支持WIndows,Linux,Mac等系统使用，目前仅提供Windows版，后续会进行更新其他平台的。内存占用在300M左右（之前在230左右，在某次更新中突然爆增，开发者死也不承认代码水平过烂导致）。该软件会在后续优化后进行开源，以供JavaFx初学者参考学习。
  本项目仅供学习交流使用，请在。。。。
![image.png](https://cdn.nlark.com/yuque/0/2023/png/22760263/1679007735453-cb508bd6-badb-4590-9ff7-e06a8e2e7639.png#averageHue=%23393837&clientId=u6fa5cb53-4cb5-4&from=paste&height=833&id=uefcf0d20&name=image.png&originHeight=833&originWidth=1307&originalType=binary&ratio=1&rotation=0&showTitle=false&size=382699&status=done&style=none&taskId=u0f310e5e-1217-41fc-8018-f3bbe919cbc&title=&width=1307)
# 二、特点
## GalGame模式
![image.png](https://cdn.nlark.com/yuque/0/2023/png/22760263/1679008085057-2462849e-d070-4865-ac24-1e13acb3110b.png#averageHue=%238c7d9f&clientId=u6fa5cb53-4cb5-4&from=paste&height=833&id=u482bbb86&name=image.png&originHeight=833&originWidth=1307&originalType=binary&ratio=1&rotation=0&showTitle=false&size=1337776&status=done&style=none&taskId=ub6f2a1d3-b0b7-4abb-b8eb-b0861a35799&title=&width=1307)
![image.png](https://cdn.nlark.com/yuque/0/2023/png/22760263/1679008442119-01fe75ce-dcfe-4577-8187-620de63f17da.png#averageHue=%2393bbc2&clientId=u6fa5cb53-4cb5-4&from=paste&height=833&id=u3e8e4d9b&name=image.png&originHeight=833&originWidth=1307&originalType=binary&ratio=1&rotation=0&showTitle=false&size=1051686&status=done&style=none&taskId=u6f4d7a41-76c0-48e7-ab1e-11281983571&title=&width=1307)
![image.png](https://cdn.nlark.com/yuque/0/2023/png/22760263/1679008199394-ca796a56-2157-4a60-bde8-652912614880.png#averageHue=%23b6b09c&clientId=u6fa5cb53-4cb5-4&from=paste&height=833&id=u01a208ef&name=image.png&originHeight=833&originWidth=1307&originalType=binary&ratio=1&rotation=0&showTitle=false&size=378580&status=done&style=none&taskId=u03f842d8-e5ba-4203-a7d0-36de96f5bed&title=&width=1307)
## 传统模式

![image.png](https://cdn.nlark.com/yuque/0/2023/png/22760263/1679008121741-5006a2aa-8c6a-48ff-a5ea-852bc1ad29da.png#averageHue=%2396a992&clientId=u6fa5cb53-4cb5-4&from=paste&height=833&id=u84e2e6b2&name=image.png&originHeight=833&originWidth=1307&originalType=binary&ratio=1&rotation=0&showTitle=false&size=584275&status=done&style=none&taskId=u6df1042e-4fe3-4391-a36e-0bd72e83768&title=&width=1307)

# 三、使用说明
## 1.约定大于配置
### （1）关于背景
  未来道具10号支持更换背景，需要在音声目录中设置背景图片的名称以"background"开头，假设A音声有背景图片"B.png"，那么必须将“B.png”改名为"background01.png"；以次类推，多了背景图命名为"background02.png"，"background03.jpg"，"background04.jpeg"；
  当然，软件提供快捷方式改名，只要在图片上右键出现的快捷菜单中选择"设为背景"，可直接命名![image.png](https://cdn.nlark.com/yuque/0/2023/png/22760263/1679008972750-d8ae8ef4-c5de-45bb-98e6-9f0738be78c6.png#averageHue=%23373534&clientId=u6fa5cb53-4cb5-4&from=paste&height=833&id=udfb1d156&name=image.png&originHeight=833&originWidth=1307&originalType=binary&ratio=1&rotation=0&showTitle=false&size=210362&status=done&style=none&taskId=u3ad9691a-878e-4929-a066-634fb0d564e&title=&width=1307)
### （2）关于封面及音声数据（类别，社团等）
   音声封面及音声数据支持智能同步，数据来源于某D站，国内记得在设置中配置代理服务器，不然大概率无法同步。你可以在音声上右键选择同步该音声，也可以在右下角选择同步当页所有音声
![image.png](https://cdn.nlark.com/yuque/0/2023/png/22760263/1679009383891-80f8ac18-d5c8-4807-bbe9-1de8813278cb.png#averageHue=%23383535&clientId=u6fa5cb53-4cb5-4&from=paste&height=833&id=u2eef816a&name=image.png&originHeight=833&originWidth=1307&originalType=binary&ratio=1&rotation=0&showTitle=false&size=156971&status=done&style=none&taskId=u1bdbb7ed-9aec-4e54-8e2d-5081c49c9c8&title=&width=1307)
### （3）关于字幕歌词和音声文件夹命名
软件支持LRC格式的字幕歌词，但无法判断音声是否存在字幕文件从而显示字幕标签，推荐在文件夹命名上带上“字幕”，这样即可识别。
文件夹推荐命名:【字幕】XXXX【RJXXXXX】
当然如果使用类似“xxxz字幕xxxRJYYYYYYxxx”等包含关键字的命名也是可以识别到的，但其他的无法自动识别，只能通过手动修改。
### （4）关于更新
  目前没有实现更新功能，你可以star本项目也可以加群随时获取
# 四、后续开发

- 搜索排序
- 换肤
- UI调整
- 如果你有好的建议，欢迎和开发者进行    深切交流
# 五、感谢以下开源项目

1. [RXControls](https://github.com/leewyatt/rxcontrols)
2. [JFoenix](https://github.com/sshahine/JFoenix)
3. [FXTrayIcon](https://github.com/dustinkredmond/FXTrayIcon)
4. [ControlsFX](https://github.com/controlsfx/controlsfx)
5. [Jackson](https://github.com/FasterXML/jackson)
6. [Mybatis](https://github.com/mybatis/mybatis-3)
# 六、为爱发电项目，永久免费，日渐消瘦，概不负责
喜欢本程序欢迎请开发者补充一瓶营养快线，加上BUFF
