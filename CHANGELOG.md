# RecSync Client 更新日志

## [1.2.3] - 2025-01-09

### 🔥 重大Bug修复

#### 修复打包后无法启动摄像头的问题（原生库加载失败）

**问题描述：**
- 在IntelliJ中运行正常，但打包成EXE后摄像头无法启动
- ClientApplication.java:871行抛出严重错误
- 错误类型：UnsatisfiedLinkError - 无法加载原生库

**根本原因：**
- **jlink打包时JavaCV的原生库（DLL文件）没有被正确处理**
- 原生库被复制到了嵌套的子目录（org/bytedeco/.../windows-x86_64/*.dll）
- Java无法从java.library.path找到这些嵌套目录中的DLL
- 在IDE中运行时，Gradle会自动处理classpath中的原生库，但打包后机制不同

**修复方案：**

1. **添加copyNativeLibs任务：**
   - 从JavaCV依赖的JAR包中提取所有原生库（.dll/.so/.dylib）
   - 复制到jlink镜像的bin目录

2. **添加flattenNativeLibs任务：**
   - 将嵌套在子目录中的DLL文件"扁平化"
   - 全部复制到bin根目录，确保java.library.path能找到
   - 关键DLL包括：
     - jniopencv_*.dll（OpenCV JNI绑定）
     - jniavcodec.dll等（FFmpeg JNI绑定）
     - jniopenblas*.dll（OpenBLAS JNI绑定）
     - jnijavacpp.dll（JavaCPP基础库）

3. **配置任务执行顺序：**
   ```gradle
   jlink -> copyNativeLibs -> flattenNativeLibs
   ```

**修改文件：**
- `desktop-client/build.gradle`
  - 新增 `copyNativeLibs` 任务
  - 新增 `flattenNativeLibs` 任务
  - 配置任务依赖关系

**技术细节：**
```gradle
// 从JAR中提取原生库
from {
    configurations.runtimeClasspath.filter {
        it.name.contains('javacpp') ||
        it.name.contains('opencv') ||
        it.name.contains('ffmpeg') ||
        it.name.contains('openblas')
    }.collect { zipTree(it) }
}

// 扁平化到bin根目录
fileTree(binDir).matching {
    include '**/*.dll'
}.each { file ->
    if (file.parentFile.name != 'bin') {
        copy {
            from file
            into binDir
        }
    }
}
```

**用户影响：**
- ✅ 打包后的EXE现在可以正常启动摄像头
- ✅ 原生库加载问题彻底解决
- ✅ IntelliJ和打包版本行为一致
- ✅ 不再出现UnsatisfiedLinkError

**验证方法：**
1. 运行打包后的RecSync-Client.bat
2. 观察状态栏应显示"摄像头已启动"
3. 预览窗口应显示摄像头画面
4. 不再出现"相机严重错误"弹窗

**已打包位置：**
```
desktop-client\build\image\bin\RecSync-Client.bat
desktop-client\build\image\bin\*.dll (包含所有JavaCV原生库)
```

---

## 历史版本

### [1.2.2] - 2025-01-08
- 增强错误处理和用户反馈

### [1.2.1] - 2025-01-08
- 修复配置文件保存路径问题

### [1.2.0] - 2025-01-08
- 修复相机启动UnsatisfiedLinkError
- 恢复多摄像头选择功能

### [1.1.3] - 2025-01-08
- 移除多摄像头选择功能（已废弃）
