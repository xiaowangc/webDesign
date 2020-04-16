package mymvc;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;

public class DispatcherServlet extends HttpServlet {

    /**
     * 1.DispatcherServlet这个总管首相需要找到相应请求的Controller类
     * 2.利用反射根据Controller类找到这个类所在的类全名，并通过接受参数找到对应的请求方法
     * 3.执行相应的请求方法
     * 4.得到执行方法后返回的值，作请求转发的响应
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    /**
     * 每个缓存集合的说明：
     * controllerMap<String,String> ： 用来存放从配置文件中读取到的内容，键为controller名字，值为controller类全名
     * 如 （键）AtmController=controller.AtmController(值)
     * objectHashMap<String,Object> ： 用来存放controller类名字对应的controller对象
     * 如 String类型为 AtmController Object就为atmController对象
     * objectMethodMap<Object,Map<String,Method>>：用来存放具体controller对象里面的所有method方法对象
     * 如：object类型为atmController对象， Map<String,Method>为每个方法名对应的method对象
     */

    //单例，生命周期托管IOC形式来管理所有Controller对象,这里String类型为相应的controller类名字
    //Object为Controller对象，利用Controller对象来找寻该对象下的所有method方法
    private HashMap<String, Object> objectHashMap = new HashMap<>();
    //存放每个对象中的所有方法
    private Map<Object, Map<String, Method>> objectMethodMap = new HashMap<>();

    //controllerMap对象用来存放ApplicationContext配置文件中的内容，作为缓存
    private HashMap<String, String> controllerMap = new HashMap<>();//将类名和类全名存放起来
    //解析方法上面的注解  获取注解里面的请求名字（方法名）   并和相应的类全名存放起来
    //  一个是方法名  一个是类全名
    private HashMap<String, String> methodWithRealNameMap = new HashMap<>();//将方法名和类全名存放起来

    //1号小弟.初始化加载 从配置文件中根据类名找到类全名 并放置在controllerMap中
    private void load() {
        try {
            Properties properties = new Properties();
            properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("ApplicationContext.properties"));
            Enumeration en = properties.propertyNames();
            while (en.hasMoreElements()) {
                String key = (String) en.nextElement();
                String value = properties.getProperty(key);
                controllerMap.put(key, value);
//                System.out.println(key + "=" + value);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //2号小弟 解析uri
    private String parseUri(String uri) {
        String realUri = uri.substring(uri.lastIndexOf("/") + 1);
        System.out.println("This is the request of uri :" + realUri);
        return realUri;
    }

    //3号小弟 通过类名找到对象
    private Object findObject(String requestContent) throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        String realControllerName = controllerMap.get(requestContent);
        if (realControllerName == null) {
            realControllerName = methodWithRealNameMap.get(requestContent);
            if (realControllerName == null) {
                //404
                throw new ControllerNotFoundException(requestContent + "资源没有找到");
            }
        }
        System.out.println("the realControllerName is " + realControllerName);
        //这样通过类全名就可以通过反射获取对应的Controller类
        Object object = objectHashMap.get(requestContent);
        //下面几行代码做了一个生命周期托管的形式，延迟加载，单例
        if (object == null) {
            //反射得到请求对应的Controller类
            Class clazz = Class.forName(realControllerName);
            Constructor constructor = clazz.getConstructor();
            object = constructor.newInstance();
            objectHashMap.put(requestContent, object);
            //找到Controller类对象的所有方法
            Method[] methods = clazz.getDeclaredMethods();
            Map<String, Method> methodMap = new HashMap<>();
            for (Method method : methods) {
                RequestMapping methodAnnotation = method.getAnnotation(RequestMapping.class);
                if (methodAnnotation != null) {
                    methodMap.put(methodAnnotation.value().substring(0, methodAnnotation.value().lastIndexOf(".")), method);
                }
                methodMap.put(method.getName(), method);
            }
            //将小的map存入大的存储对象下所有方法的map中
            objectMethodMap.put(object, methodMap);
        }
        return object;
    }

    //4号小弟 根据请求得到的方法找到方法
    private Method findMethod(Object obj, String methodName) {
        //methodMap可能为空，，因为如果配置文件中不配置AtmController=controller.AtmController这样格式时，就为空
        Map<String, Method> methodMap = objectMethodMap.get(obj);
        return methodMap.get(methodName);
    }

    //5号小弟 将方法上的所有参数转换成object类型对象数组    ！！！非常重要！！！
    //负责解析方法中的所有参数 做自动注入 DI依赖注入 Dependency Injection
    private Object[] injectionParameters(Method method, HttpServletRequest request, HttpServletResponse response) throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {

        //1.解析这个method   目的是为了做方法参数的自动注入
        Parameter[] parameters = method.getParameters();
//        System.out.println("the length of parameters is " + parameters.length);
        if (parameters == null || parameters.length == 0) {
//            System.out.println("the parameters is null");
            return null;
        }

        //2.创建一个Object数组 目的是为了存放所有参数类型的值 最终返回出去 交给invoke方法执行使用
        Object[] findParamValue = new Object[parameters.length];
        //3.分析方法参数上的类型：可能为 零散类型（包括基本数据类型、引用数据类型） map domain对象
        for (int i = 0; i < parameters.length; i++) {
//          注意：  parameters[i].getName() 这个方法不能获取参数的名字
            //  获取参数的名字的主要方法：在参数前面加上注解
            //  先分析参数类型前面是否加了注解：如果有注解则为零散类型
            //3.1 参数类型为零散类型
            //3.2 参数类型为map类型
            //3.3 参数类型为domain对象
            Parameter parameter = parameters[i];
            Param paramAnnotation = parameter.getAnnotation(Param.class);
            if (paramAnnotation != null) {//参数类型为零散类型
                String requestValue = request.getParameter(paramAnnotation.value());
                //判断是否存在requestValue
                if (requestValue != null) {//继续判断参数类型
                    Class paramClazz = parameter.getType();
                    if (paramClazz == String.class) {
                        findParamValue[i] = new String(requestValue);
                    } else if (paramClazz == Integer.class || paramClazz == int.class) {
                        findParamValue[i] = new Integer(requestValue);
                    } else if (paramClazz == Float.class || paramClazz == float.class) {
                        findParamValue[i] = new Float(requestValue);
                    } else if (paramClazz == Double.class || paramClazz == double.class) {
                        findParamValue[i] = new Double(requestValue);
                    } else if (paramClazz == Boolean.class || paramClazz == boolean.class) {
                        findParamValue[i] = new Boolean(requestValue);
                    }

                }

            } else {
                //参数类型可能为 request对象、response对象、map类型 或者domain对象类型
                Class paramClazz = parameter.getType();
                if (paramClazz == HttpServletRequest.class) {
                    findParamValue[i] = request;
                    continue;
                } else if (paramClazz == HttpServletResponse.class) {
                    findParamValue[i] = response;
                    continue;
                }
                Object paramObject = paramClazz.newInstance();
                if (paramObject instanceof Map) {
                    //将map填满
                    Map paramMap = (Map) paramObject;
                    Enumeration<String> requestEnum = request.getParameterNames();
                    while (requestEnum.hasMoreElements()) {
                        String key = requestEnum.nextElement();
                        String value = request.getParameter(key);
                        paramMap.put(key, value);
                    }
                    findParamValue[i] = paramMap;
                } else if (paramObject instanceof Object) {
                    //当方法参数为对象时，利用反射为对象上的属性赋值
                    //将对象属性的set方法执行即可
                    //1.获取对象的所有属性
                    Field[] paramField = paramClazz.getDeclaredFields();
                    for (Field field : paramField) {//遍历每个属性
                        field.setAccessible(true);
                        Class fieldClazz = field.getType(); //属性类型
                        //2.获取属性类型的构造方法,构造方法参数为String类型的
                        Constructor fieldCon = fieldClazz.getConstructor(String.class);
                        //3.执行属性的set方法，可为该属性赋值，set方法需要对象，和真正的值为属性赋值
                        field.set(paramObject, fieldCon.newInstance(request.getParameter(field.getName())));
                    }
                    findParamValue[i] = paramObject;
                } else {
                    //其他类型的，处理不了了
                }
            }


        }
        //首先需要获取参数的名字key 作为request.getParameter的key值
        //通过request.getParameter("key")这个方法是为了获取请求的参数 存起来 key是方法上参数的名字
        return findParamValue;
    }

    //6号小弟 负责解析响应信息
    private void findHandleResponseContent(Object methodResult, Method method, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (methodResult != null) { //methodResult有响应信息,可以处理，可能为ModelAndView类型或者String类型
            if (methodResult instanceof ModelAndView) { //可能性1：为ModelAndView类型
                ModelAndView modelAndView = (ModelAndView) methodResult;
                //设计一个方法用来负责分析ModelAndView对象的属性情况
                this.parseModelAndView(modelAndView, request, response);
            } else if (methodResult instanceof String) {
                String viewName = (String) methodResult;
                this.parseString(viewName, request, response);
            } else { //处理JSON形式   需要多定义一个注解
                //如果一个controller类对象的普通方法需要以JSON的形式交给view层处理，需要在该方法前加一个注解ResponseBody
                //表示该方法的一个返回信息需要交给JSON来处理
                ResponseBody responseBody = method.getAnnotation(ResponseBody.class);
                if (responseBody != null) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("jsonObject", methodResult);
                    response.getWriter().write(jsonObject.toJSONString());
                }
            }
        } else {
            //没有响应信息，处理不了了，可以考虑抛出异常
            System.out.println("there isn't response information.");
        }

    }

    //6.1号小弟 负责帮忙解析ModelAndView对象中map集合
    private void parseModelAndView(ModelAndView modelAndView, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //1.遍历map集合，取出里面的所有键值注入request.setAttribute(key,Object)方法中
        HashMap<String, Object> attributeMap = modelAndView.getAttributeMap();
        Iterator<String> it = attributeMap.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            String value = (String) modelAndView.getAttribute(key);
            request.setAttribute(key, value);
        }
        //2.负责处理String类型的属性   这块地方的处理与视频的不一样
        //如果viewName为空的话就不进行处理了
        String viewName = modelAndView.getViewName();
        this.parseString(viewName, request, response);
    }

    //6.2号小弟 用来处理String的路径问题 转发还是重定向
    private void parseString(String viewName, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //先进行严谨性判断
        if (viewName != null) {
            if (!"".equals(viewName) && !"null".equals(viewName)) {
                String[] viewNames = viewName.split(":");
                //1.viewNames[0]为redirect，则重定向
                //2.viewNames长度为1则为转发
                if (viewNames.length == 1) {//转发
                    request.getRequestDispatcher(viewName).forward(request, response);
                } else if ("redirect".equals(viewNames[0])) {
                    response.sendRedirect(viewNames[1]);
                }
            }
        }

    }

    //设计一个方法，负责扫描controller包下的所有类中的注解对象
    private void scanAnnotation() {
        String allPackageName = controllerMap.get("scanPackage");
        if (allPackageName != null) { //类上有注解
            String[] packageNames = allPackageName.split(",");
            for (String packageName : packageNames) {
                //通过包名获取该包在工程目录在硬盘上的真实全路径
                URL url = Thread.currentThread().getContextClassLoader().getResource(packageName.replace(".", "/"));

                String packagePath = url != null ? url.getPath() : null;//获取包的真实路径
                if (packagePath != null) {
                    File files = new File(packagePath);
                    File[] fileList = files.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File file) {
                            if (file.isFile() && file.getName().endsWith("class")) {
                                return true;
                            }
                            return false;
                        }
                    });
                    for (File file : fileList) { //遍历controller文件夹下的所有controller.class文件
                        //fileClassName 为类名
                        String fileClassName = file.getName().substring(0, file.getName().indexOf("."));
                        //构建类全名  包名+类名
                        String allClassName = packageName + "." + fileClassName;
                        try {
                            Class clazz = Class.forName(allClassName);
                            RequestMapping classAnnotation = (RequestMapping) clazz.getAnnotation(RequestMapping.class);
                            if (classAnnotation != null) { //证明类上有注解
                                controllerMap.put(classAnnotation.value(), allClassName);
                            } //类上没有注解，接着看方法是否有注解
                            Method[] methods = clazz.getDeclaredMethods();
                            for (Method method : methods) {
                                RequestMapping methodAnnotation = method.getAnnotation(RequestMapping.class);
                                if (methodAnnotation != null) {
                                    methodWithRealNameMap.put(methodAnnotation.value(), allClassName);
                                }
                            }

                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }

                    }
                }
            }

        } else { //类上没有注解
            System.out.println("The head of Class no annotation");
        }

    }


    @Override
    public void init() throws ServletException {
        this.load();
        this.scanAnnotation();
    }

    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        try {
            //1.接受请求uri
            String uri = request.getRequestURI();
//            System.out.println("The value of uri is"+uri);
            //2.解析获取uri的具体请求名,此时解析uri后requestContent可能为AtmController
            String requestContent = this.parseUri(uri);
//            System.out.println("After parse:"+requestContent);
            //3.获取方法名
            String methodName = request.getParameter("method");
            if (methodName == null) {
                methodName = requestContent.substring(0, requestContent.lastIndexOf("."));
            }
            //4.通过类名requestContent找到对象
            Object obj = this.findObject(requestContent);
            //5.通过对象、请求方法名 找到对象的Method
            Method method = this.findMethod(obj, methodName);//错误查找：obj不为空
            //6.获取执行方法时需要的那些条件(自动注入参数)
            Object[] findParamValue = this.injectionParameters(method, request, response);
            //7.获取响应信息
            Object methodResult = (String) method.invoke(objectHashMap.get(requestContent), findParamValue);
            System.out.println("the main body methodResult is " + methodResult);
            //8.处理响应信息
            this.findHandleResponseContent(methodResult, method, request, response);

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
//        } catch (InvocationTargetException e) {
//            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            throw new MethodNoParamException("the method body has no Param");
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }


    }
}


/**
 * 注意：
 * 通过类名找到对象这个方法findObject(String uri)中做了生命周期托管的单例，延迟加载
 * 6.获取执行方法时需要的那些条件(自动注入参数)
 */

