package moe.xzr.oplushidezoomwindow;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Entry implements IXposedHookLoadPackage {
        @Override
        public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
                if (!"android".equals(lpparam.packageName)) // 仅处理系统框架进程
                        return;

                // Part 1: 通过修改窗口标志隐藏特定窗口
                Class<?> windowStateAnimatorClass = // 获取WindowStateAnimator类引用
                                XposedHelpers.findClass("com.android.server.wm.WindowStateAnimator",
                                                lpparam.classLoader);
                // Hook WindowSurfaceController的构造函数
                // Part 1: 通过修改窗口标志隐藏特定窗口
                XposedHelpers.findAndHookConstructor("com.android.server.wm.WindowSurfaceController",
                        lpparam.classLoader, 
                        String.class,      // 参数1：name
                        int.class,         // 参数2：w
                        int.class,         // 参数3：h
                        windowStateAnimatorClass, // 参数4：animator
                        int.class,         // 参数5：flags（原参数4）
                        boolean.class,     // 新增参数6：isTrusted（Android13新增）
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                // 参数索引从3改为4（因新增isTrusted参数）
                                Object windowStateAnimator = param.args[4]; // 原索引3 → 4
                                Object windowState = XposedHelpers.getObjectField(windowStateAnimator, "mWin");
                                
                                // 获取窗口模式
                                int mode = (int) XposedHelpers.callMethod(windowState,
                                                "getWindowingMode");
                                
                                // 判断是否为缩放窗口
                                boolean isZoomWindow = (boolean) XposedHelpers.callMethod(
                                                windowStateExt, "checkIfWindowingModeZoom", mode);
                                // 获取窗口所属包名
                                String owningPackage = (String) XposedHelpers.callMethod(windowState,
                                                "getOwningPackage");
                                // 获取窗口类型名称
                                String typeName = XposedHelpers.callMethod(windowState, "getWindowTag")
                                                .toString();
                                
                                // 判断需要隐藏的窗口类型：
                                if (isZoomWindow || // 缩放窗口本身
                                                "com.oplus.appplatform".equals(owningPackage) || // 边缘面板
                                                "OplusOSZoomFloatHandleView".equals(typeName) || // 边缘手柄
                                                "com.oplus.screenshot/LongshotCapture".equals(typeName)
                                                || // 截图预览
                                                "com.coloros.smartsidebar".equals(typeName) || // 智能侧边栏
                                                "InputMethod".equals(typeName)) { // 输入法
                                        // 添加FLAG_HIDDEN标志 (0x00000040)
                                        // flags参数位置从2改为3（因新增isTrusted参数）
                                        int flags = (int) param.args[3]; // 原索引2 → 3
                                        flags |= 0x00000040;
                                        param.args[3] = flags; // 原索引2 → 3
                                }
                            }
                        });
                
                // Part2: Do not render shadow behind zoom window, otherwise it'll still appear.
                XposedHelpers.findAndHookMethod("com.android.server.wm.Task",
                                lpparam.classLoader, "getShadowRadius", boolean.class,
                                XC_MethodReplacement.returnConstant(0.0f));

                // Part3: Rebuild SurfaceController on window mode changes to make sure Part1
                // will get
                // applied instantly.
                XposedHelpers.findAndHookMethod("com.android.server.wm.Task",
                                lpparam.classLoader, "setWindowingModeInSurfaceTransaction",
                                int.class, boolean.class, new XC_MethodHook() {
                                        @Override
                                        protected void afterHookedMethod(MethodHookParam param) {
                                                Object windowState = XposedHelpers.callMethod(
                                                                param.thisObject, "getTopVisibleAppMainWindow");
                                                Object activityRecord = XposedHelpers.callMethod(
                                                                param.thisObject, "getTopVisibleActivity");
                                                Object rootWindowContainer = XposedHelpers.getObjectField(
                                                                param.thisObject, "mRootWindowContainer");
                                                if (windowState == null)
                                                        return;
                                                XposedHelpers.callMethod(windowState, "destroySurfaceUnchecked");
                                                XposedHelpers.callMethod(activityRecord, "stopIfPossible");
                                                XposedHelpers.callMethod(
                                                                rootWindowContainer, "resumeFocusedTasksTopActivities");
                                        }
                                });
        }
}
