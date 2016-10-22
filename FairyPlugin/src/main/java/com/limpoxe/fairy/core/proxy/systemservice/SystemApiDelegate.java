package com.limpoxe.fairy.core.proxy.systemservice;

import com.limpoxe.fairy.content.PluginDescriptor;
import com.limpoxe.fairy.core.PluginLoader;
import com.limpoxe.fairy.manager.PluginManagerHelper;
import com.limpoxe.fairy.core.proxy.MethodDelegate;
import com.limpoxe.fairy.core.proxy.MethodProxy;
import com.limpoxe.fairy.util.LogUtil;

import java.lang.reflect.Method;

/**
 * 所有被hook的系统服务的所有api方法调用, 都会先进入这里
 * Created by cailiming on 16/7/14.*
 */
public class SystemApiDelegate extends MethodDelegate {

    private final String descriptor;

    public SystemApiDelegate(String descriptor) {
        this.descriptor = descriptor;
    }

    public Object beforeInvoke(Object target, Method method, Object[] args) {
        LogUtil.v("beforeInvoke", descriptor, method.getName());

        //这里做此判定是为了把一些特定的接口方法仍然交给特定的MethodProxy去处理,不在此做统一处理
        //这些"特定的MethodProxy"主要是一些查询类接口
        //另外, 这里单独判断checkPackage是因为AppOpsService的checkPackage方法会进入这里, 而if里面的replacePackageName方法里
        // 面会触发一次ContentProvider调用, ContentProvider调用又会触发AppOpsService的checkPackage方法,
        // AppOpsService的checkPackage方法被触发后又回进入这里, 造成递归异常,因此这里单独屏蔽掉checkPackage方法
        if (!MethodProxy.sMethods.containsKey(method.getName()) && !"checkPackage".equals(method.getName())) {
            fixPackageName(method.getName(), args);
        }

        return null;
    }

    @Override
    public Object afterInvoke(Object target, Method method, Object[] args, Object beforeInvoke, Object invokeResult) {
        if ("android.view.IWindowManager".equals(descriptor)) {
            LogUtil.v("afterInvoke", descriptor, method.getName());
            if ("openSession".equals(method.getName())) {
                if (invokeResult != null) {
                    //TODO 暂时不hook， 这个hook会引起弹Dailog和PopupWindow时发生DeadObject异常，
                    Object windowSession = null;//AndroidViewIWindowSession.installProxy(invokeResult);
                    if (windowSession != null) {
                        return windowSession;
                    }
                }
            }
        }
        return super.afterInvoke(target, method, args, beforeInvoke, invokeResult);
    }

    /**
     * 由于插件的Context.getPackageName返回了插件自己的包名
     * 这里需要在调用binder接口前将参数还原为宿主包名
     * @param args
     */
    private void fixPackageName(String methodName, Object[] args) {
        if(args != null && args.length>0) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof String && ((String)args[i]).contains(".")) {
                    // 包含.号, 基本可以判定是packageName
                    if (!args[i].equals(PluginLoader.getApplication().getPackageName())) {
                        //尝试读取插件, 注意, 这个方法调用会触发ContentProvider调用
                        PluginDescriptor pd = PluginManagerHelper.getPluginDescriptorByPluginId((String)args[i]);
                        if(pd != null) {
                            LogUtil.v("修正System Api", descriptor, methodName, "的参数为宿主包名", args[i]);
                            // 参数传的是插件包名, 修正为宿主包名
                            args[i] = PluginLoader.getApplication().getPackageName();
                            // 这里或许需要break,提高效率,
                            // 因为一个接口的参数里面出现两个packageName的可能性较小
                            // break;
                        }
                    }
                }
            }
        }
    }
}
