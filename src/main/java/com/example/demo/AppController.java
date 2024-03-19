package com.example.demo;

import jakarta.annotation.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/sell")
public class AppController {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    String LOCK = "TICKETSELLER";
    String KEY = "TICKET";       // 记得在 redis 里面设置好 TICKET 的数量

    @GetMapping("/ticket")
    public void sellTicket() {
        String luaScript =
                "if redis.call('setnx',KEYS[1],ARGV[1]) == 1 " +
                        "then redis.call('expire',KEYS[1],ARGV[2]) ;" +
                        "return true " +
                "else return false " +
                "end";

        // 生产环境替换为 uuid + 线程 id
        String VALUE = String.valueOf(Thread.currentThread().getId());
        Boolean isLocked = stringRedisTemplate.execute(new RedisCallback<Boolean>() {
            @Override
            public Boolean doInRedis(RedisConnection connection) throws DataAccessException {
               return connection.eval(luaScript.getBytes(),
                        ReturnType.BOOLEAN,
                        1,
                        LOCK.getBytes(),
                        VALUE.getBytes(),  // 用于判断是否为当前线程加的锁
                        "10000".getBytes());
            }
        });
        if (Boolean.TRUE.equals(isLocked)) {
            try {
                int ticketCount = Integer.parseInt((String) stringRedisTemplate.opsForValue().get(KEY));
                if (ticketCount > 0) {
                    stringRedisTemplate.opsForValue().set(KEY, String.valueOf(ticketCount - 1));
                    System.out.println("I get a ticket!");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                String LOCK_ID = stringRedisTemplate.opsForValue().get(LOCK);
                if (LOCK_ID != null && LOCK_ID.equals(VALUE)) {
                    stringRedisTemplate.delete(LOCK);
                }
            }
        } else {
            System.out.println("Field");
        }
    }

}
