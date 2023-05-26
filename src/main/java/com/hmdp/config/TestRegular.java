package com.hmdp.config;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestRegular {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String str = scanner.next();
        //把长字符串中的数字和字符切割,并存入数组
        System.out.println("str = " + str);
        ArrayList<String> list = new ArrayList<>();
//        Pattern pattern= Pattern.compile("[0-9]*[a-zA-Z]*");
        Pattern pattern= Pattern.compile("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$");
        Matcher matcher = pattern.matcher(str);
        while (matcher.find()){
            list.add(matcher.group());
        }

        String[] strings1 = new String[list.size()];
        String[] strings= list.toArray(strings1);

        for (int i = 0; i < strings.length-1; i++) {
            System.out.println("strings = " + strings[i]);
        }
    }
}
