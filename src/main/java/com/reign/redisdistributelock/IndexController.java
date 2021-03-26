package com.reign.redisdistributelock;


import org.redisson.Redisson;
import org.redisson.RedissonRedLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Time;
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

                //FIXME 如果系统此时发生GC，导致线程卡在这里，锁超时，其他线程获取到锁，就会导致释放了其他线程的锁；
                //FIXME  解决方案：释放锁必须用lua脚本保证原子性释放
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
            //加锁;  TODO  如果指定锁超时时间，则不会走续命逻辑
            rLock.lock(30, TimeUnit.SECONDS);
            int stock = Integer.parseInt(stringRedisTemplate.opsForValue().get("productNum"));
            if (stock > 0) {
                int realStock = stock - 1;
                stringRedisTemplate.opsForValue().set("productNum", realStock + "");
                System.out.println("扣减成功，剩余库存:" + realStock);
            } else {
                System.out.println("扣减失败，库存不足");
            }

            //TODO 模拟线程死亡
            // throw new RuntimeException("抛错自杀");

            //TODO 模拟阻塞，通过续命
            TimeUnit.MINUTES.sleep(5);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //释放锁
            rLock.unlock();
        }

        return "end";
    }


    /**
     * 测试能够无限次重入
     *
     * @return
     */
    @RequestMapping("/retrantLock")
    public String getReentrantLock() throws InterruptedException {
        //获取一把锁;未加锁
        RLock rLock = redisson.getLock(lockKey);
        try {
                rLock.lock();
                int stock = Integer.parseInt(stringRedisTemplate.opsForValue().get("productNum"));
                if (stock > 0) {
                    int realStock = stock - 1;
                    stringRedisTemplate.opsForValue().set("productNum", realStock + "");
                    System.out.println("扣减成功，剩余库存:" + realStock+"当前线程Id"+Thread.currentThread().getId());
                } else {
                    System.out.println("扣减失败，库存不足");
                }
                TimeUnit.MINUTES.sleep(1);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //释放锁RedissonRedLock
            System.out.println("5秒后开始释放锁");
            TimeUnit.SECONDS.sleep(5);

            rLock.unlock();

        }

        return "end";
    }


    @RequestMapping("/redLock")
    public String getRedLock() throws InterruptedException {
        Config config1 = new Config();
        config1.useSingleServer().setAddress("redis://172.0.0.1:5378).setPassword(;a123456).setDatabase(0)");
        RedissonClient redissonClient1 = Redisson.create(config1);

        Config config2 = new Config();
        config2.useSingleServer().setAddress("redis://172.0.0.1:5378).setPassword(;a123456).setDatabase(0)");
        RedissonClient redissonClient2 = Redisson.create(config2);

        Config config3 = new Config();
        config3.useSingleServer().setAddress("redis://172.0.0.1:5378).setPassword(;a123456).setDatabase(0)");
        RedissonClient redissonClient3 = Redisson.create(config3);


        RLock lock1 = redissonClient1.getLock(lockKey);
        RLock lock2 = redissonClient2.getLock(lockKey);
        RLock lock3 = redissonClient3.getLock(lockKey);
        RedissonRedLock redLock = new RedissonRedLock(lock1, lock2, lock3);

        try {

            /**
             * 4.尝试获取锁
             * waitTimeout 尝试获取锁的最大等待时间，超过这个值，则认为获取锁失败
             * leaseTime   锁的持有时间,超过这个时间锁会自动失效（值应设置为大于业务处理的时间，确保在锁有效期内业务能处理完）
             */
            boolean res = redLock.tryLock(10, 5, TimeUnit.SECONDS);

            if (res) {

                //成功获得锁，在这里处理业务
            }
        } catch (Exception e) {
            throw new RuntimeException("lock fail");
        }finally{
            //无论如何, 最后都要解锁
            redLock.unlock();
        }

        return "end";
    }

}
