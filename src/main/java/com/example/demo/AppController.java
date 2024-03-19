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
        String lockLuaScript =
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
                return connection.eval(lockLuaScript.getBytes(),
                        ReturnType.BOOLEAN,
                        1,
                        LOCK.getBytes(),
                        VALUE.getBytes(),  // 用于判断是否为当前线程加的锁
                        "5".getBytes()
                );
            }
        });
        if (Boolean.TRUE.equals(isLocked)) {
            // 判断是否是自己加的锁，如果是则续期
            String addlockLuaScript =
                    "if redis.call('get',KEYS[1]) == ARGV[1] " +
                            "then redis.call('expire',KEYS[1], ARGV[2]) ; " +
                            "return true " +
                            "else return false " +
                            "end";
            Thread watchDoge = new Thread(() -> {
                while (Boolean.TRUE.equals(stringRedisTemplate.execute(new RedisCallback<Boolean>() {
                    @Override
                    public Boolean doInRedis(RedisConnection connection) throws DataAccessException {
                        return connection.eval(addlockLuaScript.getBytes(),
                                ReturnType.BOOLEAN,
                                1,
                                LOCK.getBytes(),
                                VALUE.getBytes(),
                                "5".getBytes());
                    }
                })) && !Thread.currentThread().isInterrupted()) {
                    try {
                        System.out.println(Thread.currentThread().isInterrupted());
                        Thread.sleep(4000);
                    } catch (Exception e) {
                        break;
                    }
                }
            });
            watchDoge.setDaemon(true);
            watchDoge.start();
            try {
                int ticketCount = Integer.parseInt((String) stringRedisTemplate.opsForValue().get(KEY));
                if (ticketCount > 0) {
                    stringRedisTemplate.opsForValue().set(KEY, String.valueOf(ticketCount - 1));
//                    Thread.sleep(10000000);  // 在这里睡一下，可以到 redis 里面 TTL TICKETSELLER 查看锁是否被续期
                    watchDoge.interrupt();
                    System.out.println("I get a ticket!");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
//                // 判断是否是自己加的锁，如果是则释放 缺点：非原子操作
//                String LOCK_ID = stringRedisTemplate.opsForValue().get(LOCK);
//                if (LOCK_ID != null && LOCK_ID.equals(VALUE)) {
//                    stringRedisTemplate.delete(LOCK);
//                }
                String unlockLuaScript =
                        "if redis.call('get',KEYS[1]) == ARGV[1] " +
                                "then redis.call('del',KEYS[1]); " +
                                "return true " +
                                "else return false " +
                                "end";
                stringRedisTemplate.execute(new RedisCallback<Object>() {
                    @Override
                    public Object doInRedis(RedisConnection connection) throws DataAccessException {
                        return connection.eval(unlockLuaScript.getBytes(),
                                ReturnType.BOOLEAN,
                                1,
                                LOCK.getBytes(),
                                VALUE.getBytes()
                        );
                    }
                });
            }
        } else {
            System.out.println("Field");
        }
    }

}
