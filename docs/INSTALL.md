# 安装与回退
包名 `dev.jobscn.tvhome`，入口 `.HomeActivity`。首次安装先试用，不先更改默认 Home。

## 0.1.1 换签注意
0.1.0 的发行私钥未能恢复，经用户同意，0.1.1 改用新的发行证书。**不能覆盖安装 0.1.0，需先恢复原桌面为默认，再卸载旧版后安装。卸载会丢失栖庭的本地收藏、排序、主题及播放器选择。** 不要卸载原小米桌面或小爱。后续版本必须保留并继续使用 0.1.1 的发行私钥。

## 安装

```sh
adb install atrium-0.1.1.apk
adb shell am start -n dev.jobscn.tvhome/.HomeActivity
```
在当前桌面的应用列表也可找到「栖庭 · Atrium」。方向键导航，确认打开，菜单键或长按应用整理收藏，返回退回常用；首页返回不退出。

设置→设为默认桌面：仅打开系统允许的选择流程；取消不修改。设置→更改默认桌面可恢复原 Home，若固件无入口则需电脑回退。临时打开小米桌面不更改默认。实际 Home 键路由必须在目标电视验证。

媒体控制需用户主动授予系统通知访问权限，仅观察活动媒体会话，不采集通知。无会话时打开最近播放器，不承诺冷启动恢复播放。未提供授权入口时降级为打开应用。

VPN 状态是系统存在 VPN 的信号，不代表 Sockstun 的连接状态。首版只打开 Sockstun，不替用户授权或启停网络。

## 小米电视回退
先确认电脑连接的是目标电视，原桌面仍安装且未被修改，再由用户执行：
```sh
adb shell cmd package set-home-activity --user 0 com.mitv.tvhome
adb shell am start --user 0 -n com.mitv.tvhome/.MainActivityUserMode
adb shell cmd role get-role-holders --user 0 android.app.role.HOME
```
然后检查实体 Home、小爱语音键和远场唤醒。不得禁用、卸载、清数据或强停原桌面/小爱包；本应用不执行这些动作。不要修改分辨率、系统UID或无障碍设置。
