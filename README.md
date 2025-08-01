# 项目介绍
为Flutter打造的PAG动画组件，以外接纹理的方式实现。

**注：如果遇到使用问题请在本仓库提 issue 与作者讨论，或直接提交 pr 参与共建。**

[**PAG官网**](https://pag.art/)

# 快速上手
Flutter侧通过PAGView来使用动画

### 引用
```
dependencies:
  pag: 1.0.0
```

Android端混淆文件中配置，避免影响
```
-keep class org.libpag.**{*;}
```

### 使用本地资源
```
PAGView.asset(
    "assets/xxx.pag", //flutter侧资源路径
    repeatCount: PagView.REPEAT_COUNT_LOOP, // 循环次数
    initProgress: 0.25, // 初始进度
    key: pagKey,  // 利用key进行主动调用
    autoPlay: true, // 是否自动播放
  )
```
### 使用网络资源
```
PAGView.url(
    "xxxx", //网络链接
    repeatCount: PagView.REPEAT_COUNT_LOOP, // 循环次数
    initProgress: 0.25, // 初始进度
    key: pagKey,  // 利用key进行主动调用
    autoPlay: true, // 是否自动播放
  )
```
### 使用二进制数据
```
PAGView.bytes(
    "xxxx", //网络链接
    repeatCount: PagView.REPEAT_COUNT_LOOP, // 循环次数
    initProgress: 0.25, // 初始进度
    key: pagKey,  // 利用key进行主动调用
    autoPlay: true, // 是否自动播放
  )

### 选择渲染模式
从版本1.1.0开始，支持两种渲染模式：

#### Texture模式（默认，推荐）
```dart
PAGView.asset(
    "assets/xxx.pag",
    renderMode: PAGRenderMode.texture, // Texture模式
    repeatCount: PAGView.REPEAT_COUNT_LOOP,
    autoPlay: true,
  )
```
- **优点**: 性能更好，渲染效率高
- **适用场景**: 大多数使用场景，特别是性能要求较高的场合

#### PlatformView模式
```dart
PAGView.asset(
    "assets/xxx.pag", 
    renderMode: PAGRenderMode.platformView, // PlatformView模式
    repeatCount: PAGView.REPEAT_COUNT_LOOP,
    autoPlay: true,
  )
```
- **优点**: 更好的原生集成，支持原生交互
- **缺点**: 性能略逊于Texture模式
- **适用场景**: 需要复杂原生交互的场合
- **注意**: 目前仅支持Android平台
```
### 可以在PAGView中加入回调参数
以下回调与原生PAG监听对齐
```
PAGView.asset(
    ...
    onAnimationStart: (){  // 开始
      // do something
    },
    onAnimationEnd: (){   // 结束
      // do something
    },
    onAnimationRepeat: (){ // 重复
      // do something
    },
    onAnimationCancel: (){ // 取消
      // do something
    },
```

### 通过key获取state进行主动调用
```
  final GlobalKey<PAGViewState> pagKey = GlobalKey<PAGViewState>();
  
  //传入key值
  PAGView.url(key:pagKey）
  
  //播放
  pagKey.currentState?.start();
  
  //暂停
  pagKey.currentState?.pause();  
  
  //停止
  pagKey.currentState?.stop();  
  
  //设置进度
  pagKey.currentState?.setProgress(xxx);
  
  //获取坐标位置的图层名list
  pagKey.currentState?.getLayersUnderPoint(x,y);
```
