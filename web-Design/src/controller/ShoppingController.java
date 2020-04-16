package controller;

import mymvc.RequestMapping;


@RequestMapping("ShoppingController.do")
public class ShoppingController {

    //有返回值String，参数为request,response
    public String show(){
        System.out.println("show method is executing");
        return "show.jsp";
    }
    public String kind(){
        System.out.println("kind method is executing");
        return "kind.jsp";
    }
}
