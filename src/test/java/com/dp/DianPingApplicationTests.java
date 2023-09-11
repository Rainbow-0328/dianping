package com.dp;

import com.dp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DianPingApplicationTests {


    @Autowired
    private ShopServiceImpl service;
    @Test
    public void test1(){
        service.saveShop2Redis(1L);
    }

}
