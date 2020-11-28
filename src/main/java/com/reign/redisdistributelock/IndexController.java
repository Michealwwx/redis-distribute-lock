package com.reign.redisdistributelock;


import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName: IndexController
 * @Description: TODO
 * @Author: wuwx
 * @Date: 2020-11-28 14:20
 **/
@RestController
public class IndexController {

    @Autowired
    private Redisson redisson;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public String lockKey = "lockKey";

    /**
     * 基于redis api实现的锁；无法实现续命功能
     *
     * @return
     */
    @RequestMapping("/deduct_stock")
    public String deductStock() {
        String clientId = UUID.randomUUID().toString();
        try {
            //最简单的分布式锁
//            boolean result = stringRedisTemplate.opsForValue().setIfAbsent(lockKey,"lock");
//            //设置锁过期时间
//            stringRedisTemplate.expire(lockKey,10, TimeUnit.SECONDS);
            boolean result = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, clientId, 10, TimeUnit.SECONDS);
            if (!result) {
                System.out.println("获取锁失败");
                return "error";
            }
            int stock = Integer.parseInt(stringRedisTemplate.opsForValue().get("productNum"));
            if (stock > 0) {
                int realStock = stock - 1;
                stringRedisTemplate.opsForValue().set("productNum", realStock + "");
                System.out.println("扣减成功，剩余库存:" + realStock);
            } else {
                System.out.println("扣减失败，库存不足");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //FIXME 释放锁 如果上面代码抛异常，会导致锁无法释放；
            //FIXME 所以需要用finally;但如果上面代码执行过程中机器宕机，finally块也无法执行到；
            //FIXME 设置过期时间；
            if (clientId.equals(stringRedisTemplate.opsForValue().get(lockKey))) {
                stringRedisTemplate.delete(lockKey);
            }
        }
        return "end";
    }


    /**
     * 基于redisson实现的分布式锁
     *
     * @return
     */
    @RequestMapping("/redisson_stock")
    public String redissonLock() {
        //获取一把锁;未加锁
        RLock rLock = redisson.getLock(lockKey);
        try {
            //加锁
            rLock.lock();
            int stock = Integer.parseInt(stringRedisTemplate.opsForValue().get("productNum"));
            if (stock > 0) {
                int realStock = stock - 1;
                stringRedisTemplate.opsForValue().set("productNum", realStock + "");
                System.out.println("扣减成功，剩余库存:" + realStock);
            } else {
                System.out.println("扣减失败，库存不足");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //释放锁
            rLock.unlock();
        }

        return "end";
    }


}
