package controller;


import domain.Atm;
import mymvc.Param;
import mymvc.RequestMapping;
import java.util.HashMap;

/**
 * 原来这个Controller类是个非常有规则的类
 * 需要遵循很多规则 继承 重写 名字 参数 异常
 * 经过改变这个类虽然还叫做Controller
 * 但是已经跟普通的类型非常像了，没有需要遵循那么多的规则
 *      1.没有继承关系  就一个普通类
 *      2.没有方法重写 方法名随意协
 *      3.方法参数随意  参数的目的是为了接受前面发送过来的请求信息
 *          1)HashMap   TreeMap  以后取值的时候 key就是请求传递的key
 *          2)domain对象 要求对象中的属性名 和请求的key一致
 *          3)基本类型 如String int double Float float Integer 等类型
 *              需要在这些基本类型前面添加注解@Param("key")
 *          4)方法可以是void类型或者String类型或者ModelAndView的类型
 *              1）void  ---> 在方法上添加注解，返回JSON形式
 *              2）类型String  用来表示是重定向或者请求转发
 *              3）ModelAndView类型  返回一个对象 包含路径String 和想要带走的信息
 *                  addAttribute(key,value)  setViewName("")
 */

@RequestMapping("AtmController.do")
public class AtmController{

    @RequestMapping("login.do")
    public String  login(HashMap map){

        //String name
        //String password

        System.out.println("login method is executing");
        return "login.jsp";
    }
    @RequestMapping("query.do")
    public String query(Atm atm){
        System.out.println("Query method is executing");
        return "redirect:query.jsp";
    }

}
