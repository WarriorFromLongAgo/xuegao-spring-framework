package controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("test")
public class TestController {


    public TestController() {
        System.out.println("初始化成功过");
    }

    @RequestMapping("test")
    public void test() throws Exception {
        System.out.println("cc");
    }
}

