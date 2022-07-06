# 某点阅读 7.9.228-758 自定义增强功能

## 用到的工具

* MT管理器2(反编译分析用)
* 开发者助手(类似查找 控件 **ID** 功能的都可)
* Android Studio(可有可无)
* 某点阅读(主角)

---

## 修改前截图

![图21](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/修改前.jpg?raw=true)

## 修改后截图

![图17](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/18.jpg?raw=true)
![图18](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/19.jpg?raw=true)
![图20](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/20.jpg?raw=true)

## 目录

* [自动签到](#自动签到)
* [新旧版布局](#新旧版布局)
* [本地至尊卡](#本地至尊卡)
* [去除书架右下角浮窗](#去除书架右下角浮窗)
* [去除底部导航栏中心广告](#去除底部导航栏中心广告)
* [反射方法](#反射方法)
* [开源地址](#Xposed模块开源)

### 自动签到

* 1.使用开发者助手获取 **ID** 或 **ID-HEX**

![图1](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/1.jpg?raw=true)
![图2](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/2.jpg?raw=true)

* 2.以整数类型,十六进制查找相应的 **ID-HEX**

![图3](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/3.jpg?raw=true)

* 3.查找结果一目了然,点进方法转为 Java 分析一波

![图4](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/4.jpg?raw=true)

* 4.这是它自定义控件初始化的位置,看看哪里调用
**setText** 、 **setOnClickListener** 方法(新旧布局相同思路)

![图5](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/5.jpg?raw=true)
![图6](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/6.jpg?raw=true)
![图7](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/7.jpg?raw=true)

* 5.如果是使用 **Xposed** Hook的差不多到这就行了 反射获取这个控件调用 **performClick()** 即可(甚至可以反射获取这个控件的 **getText** 方法 来判断是不是 **签到** 这两个字)

* 5.如果是反编译则添加如下代码

```smail
// p1 为 控件 注意跟在 setOnClickListener 后 参数为啥就设定啥
// 至于为何是 调用 LinearLayout 类的 performClick()
// 因为它这个 QDUIButton 继承的是 LinearLayout
// 示例 invoke-virtual {p1, v2}, Landroid/widget/LinearLayout;->setOnClickListener(Landroid/view/View$OnClickListener;)V

invoke-virtual {p1}, Landroid/widget/LinearLayout;->performClick()Z
```

#### ps:注意 如果控件没有定义 **setOnClickListener** 调用 **performClick()** 会返回 **flase** 即无效

#### ps:查找ID只演示一次 后续都是通用方法

---

### 新旧版布局

* 1.用上面的类名直接搜调用 定位到 **d()** 判断 **QDAppConfigHelper.p()** 返回的布尔值 从这两个方法内调用的类名可以确定为  **f()**  新布局 **e()** 为旧布局,很好理解了吧(不过如果直接修改这个方法有个坑,在某些版本可能会导致闪退,可以深入查看两个调用后再修改,具体可看我写的 **Xposed** 模块源码,放在最后)

![图8](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/8.jpg?raw=true)

---

### 本地至尊卡

* 无任何作用,仅仅是为了好看?

* 1.字符常量池搜索一下 **已开通** 直接有结果,就不去整那些花里胡哨的了

![图9](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/9.jpg?raw=true)

* 2.分析搜索到的相关代码块 理解为 判断 **QDAppConfigHelper.U** 返回的整数类型如果 等于2 和 不等于3 就 提示 **已开通** , 也就是说只要让它返回 **2** 即可(如上面方法一样有坑依然深入一两次调用可解决)

![图10](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/10.jpg?raw=true)

* 3.到 **QDAppConfigHelper.U** 查看代码发现是 调用了 **a.getMemberType()**, 不用管这个 **a** 是啥，继续找调用

![图11](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/11.jpg?raw=true)

* 4.到 **getMemberType()** 方法后发现里面实例化了一个 **MemberBean** 这调用来调用去的太麻烦了，干脆就全局搜了一下 **getMemberType()** 方法,结果还真有惊喜

![图12](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/12.jpg?raw=true)

*5.直接有和用户相关的类 打开发现果然有 2个疑似的方法 修改**getMemberType()** 返回值为 2 **getIsMember()** 为 1 后成功点亮本地至尊卡

![图13](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/13.jpg?raw=true)
![图14](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/14.jpg?raw=true)

---

### 去除书架右下角浮窗

* 1.炮如法制，获取 **ID-HEX** 后定位到 **onViewInject()** 方法 控件名和事件处理都和书架的效果一样 直接和上面一样改就行

* 怕有小笨蛋不知道咋改, **Xposed** Hook的话就通过反射调用 这个控件 设置 **visibility** 属性为 **View.GONE**. 反编译的则直接修改 **xml**对应 **ID** 的控件给加上 **android:visibility="gone"**

![图15](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/15.jpg?raw=true)

---

### 去除底部导航栏中心广告

* 1.分析当前 **Activity** 所属的 **Fragment** 打开方法一看就找到 **checkAdTab()** 到 参数内 **new t()** 的这个方法里面看看

![图16](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/16.jpg?raw=true)

* 2.构造函数传递了一个 **activity** 和 **String** 直接能看到关键方法 **c()**

![图17](https://github.com/xihan123/QDReadHook/blob/master/Screenshots/Tutorial/17.jpg?raw=true)

* 3.**Xposed** Hook 这个方法直接置空即可.反编译则可删除方法内容或删除上级调用

## 反射方法

```kotlin

/**
 * 通过反射获取控件
 * @param param 参数 为 Class 实例
 * @param name 字段名
 */
@Throws(NoSuchFieldException::class, IllegalAccessException::class)
inline fun <reified T : View> getView(param: Any, name: String): T? {
    return getParam<T>(param, name)
}

/**
 * 反射获取任何类型
 */
@Throws(NoSuchFieldException::class, IllegalAccessException::class)
inline fun <reified T> getParam(param: Any, name: String): T? {
    val clazz: Class<*> = param.javaClass
    val field = clazz.getDeclaredField(name)
    field.isAccessible = true
    return field[param] as? T
}

```

## Xposed模块开源

* [Lsp仓库地址](https://modules.lsposed.org/module/cn.xihan.qdds)
* [Github开源地址](https://github.com/xihan123/QDReadHook)
* [论坛地址](https://www.52pojie.cn/thread-1658012-1-1.html)
