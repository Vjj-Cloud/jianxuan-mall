package com.jack.jianxuan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableScheduling 是订单超时任务的开关。
 * 少了它，@Scheduled 标注的方法不会报错、也不会执行——属于「代码看着没问题但就是不跑」，
 * 排查起来最费时间的一类问题，所以单列一行注释说明它是必需的。
 */
@SpringBootApplication
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
