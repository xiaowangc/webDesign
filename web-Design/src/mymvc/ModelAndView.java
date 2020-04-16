package mymvc;

import javafx.beans.property.adapter.ReadOnlyJavaBeanBooleanProperty;

import java.util.HashMap;

public class ModelAndView {

    private String viewName;
    private HashMap<String,Object> attributeMap = new HashMap<>();

    //为属性赋值
    public void setViewName(String viewName){
        this.viewName = viewName;
    }
    public void addAttribute(String key,Object obj){
        attributeMap.put(key,obj);
    }
    //获取属性值的方法
    String getViewName(){
        return this.viewName;
    }
    Object getAttribute(String key){
        return attributeMap.get(key);
    }
    //获取集合
    HashMap<String,Object> getAttributeMap(){
        return this.attributeMap;
    }

}
