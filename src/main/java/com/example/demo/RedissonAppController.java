package com.example.demo;

import jakarta.annotation.Resource;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.jcache.configuration.RedissonConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/redisson")
public class RedissonAppController {

    String LOCK = "REIDSSON:TICKETSELLER";
    String KEY = "TICKET";

    // 注入
    @Resource
    RedissonClient redissonClient;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @GetMapping("/sell/ticket")
    public void redissonSellTicket() {
        /*

         */
        RLock rLock = redissonClient.getLock(LOCK);
        rLock.lock();
        try {
            int count = Integer.parseInt((String) stringRedisTemplate.opsForValue().get(KEY));
            if (count > 0) {
//                Thread.currentThread().sleep(100000000);
                stringRedisTemplate.opsForValue().set(KEY, String.valueOf(count - 1));
                System.out.println("Reidsson get ticket");
            } else {
                System.out.println("Field");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            rLock.unlock();
        }
    }

}
