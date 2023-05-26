package com.hmdp;


import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@SpringBootTest
public class RedissonTest {

    @Resource
    private RestTemplate restTemplate;

    private  Long PHONE_NUMBER=13365708000L;
    private static final String URL="http://localhost:8080/api/user/login";

    private static final String SEND_CODE_URL="http://localhost:8080/api/user/code";

    private RLock lock;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedissonClient getRedissonClient1;
    @Resource
    private RedissonClient getRedissonClient2;
    @Resource
    private RedissonClient getRedissonClient3;
    @Resource
    private RedissonClient getRedissonClient4;
    @Resource
    private RedissonClient getRedissonClient5;
    @Resource
    private RedissonClient getRedissonClient6;
    @BeforeEach
    void setUp(){
        RLock lock0 = redissonClient.getLock("order:");
        RLock lock1 = getRedissonClient1.getLock("order:");
        RLock lock2 = getRedissonClient2.getLock("order:");
        RLock lock3 = getRedissonClient3.getLock("order:");
        RLock lock4 = getRedissonClient4.getLock("order:");
        RLock lock5 = getRedissonClient5.getLock("order:");
        RLock lock6 = getRedissonClient6.getLock("order:");
        lock = redissonClient.getMultiLock(lock0, lock1, lock2, lock3, lock4, lock5, lock6);
    }


    @Test
    void method1(){
        //尝试获取锁
        boolean isLock = lock.tryLock();
        if ( ! isLock ){
            log.error("获取锁失败！。。。1");
            return;
        }
        try{
            log.info("获取锁成功。。。1");
            method2();
            log.info("准备释放锁。。。1");
        }finally {
            log.info("准备释放锁。。。1");
            lock.unlock();
        }
    }

    void method2(){
        //尝试获取锁
        boolean isLock = lock.tryLock();
        if ( ! isLock ){
            log.error("获取锁失败！。。。2");
            return;
        }
        try{
            log.info("获取锁成功。。。2");
            log.info("准备释放锁。。。2");
        }finally {
            log.info("准备释放锁。。。2");
            lock.unlock();
        }
    }

    @Test
    void batchUserTokens() throws IOException {
        StringBuffer tokens = new StringBuffer();
        for (int i = 0; i < 1000; i++) {
            LinkedMultiValueMap<String, String> strCode = new LinkedMultiValueMap<>();
            strCode.add("phone",PHONE_NUMBER.toString());
            ResponseEntity<Result> result = restTemplate.postForEntity(SEND_CODE_URL , strCode ,Result.class );
            String code = result.getBody().getCode();

            LoginFormDTO user=new LoginFormDTO();
            user.setPhone(PHONE_NUMBER.toString());
            user.setCode(code);
            HttpEntity<LoginFormDTO> objectHttpEntity2 = new HttpEntity<>(user);
            ResponseEntity<Result> stringResponseEntity = restTemplate.postForEntity(URL, objectHttpEntity2, Result.class);
            String token = (String) stringResponseEntity.getBody().getData();
            //调用方法createFileSaveTokens获取tokens
            tokens.append(token);
            tokens.append("\r\n");
            PHONE_NUMBER=PHONE_NUMBER+1;
        }

        createFileSaveTokens(tokens.toString());
        log.info("批量生成token的方法执行结束!");
    }

    private void createFileSaveTokens(String token) throws IOException {
        //指定路径创建新文件

        File file = new File("C:\\Users\\Administrator\\Desktop\\tokens.txt");
        if (file.exists()){
            log.info("目标文件已经存在!删除.....");
            file.delete();
        }
        file.createNewFile();
        //创建流工具
        FileOutputStream fos=new FileOutputStream(file);
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        PrintWriter printWriter = new PrintWriter(outputStreamWriter);

        log.info("输入的内容会写入到新创建的文档中!");
        //将需要的文件写入文档中
        if (StrUtil.isBlank(token)) {
            log.error("字符串不允许为空!");
        }
        printWriter.println(token);
        printWriter.flush();

        if (printWriter!=null){
            printWriter.close();
        }
        if (outputStreamWriter!=null){
            outputStreamWriter.close();
        }
    }

    //生成一个文档获取tokens


    @Test
    public void regExp(){
        String phoneNumber="15434534546";
        String regExt="^1([38][0-9]|5[789]|5[0-4,6-8]\\d{8})";
        boolean mismatch = mismatch(phoneNumber, regExt);
        System.out.println("mismatch = " + !mismatch);
    }

    public boolean mismatch(String str,String regExt){
        if (str==null){
            return true;
        }
        return !str.matches(regExt);
    }

    @Test
    public void test() throws InterruptedException {
        while (true){
            Thread t1 = new Thread(new PrintABC(null),"A");
            Thread t2 = new Thread(new PrintABC(t1),"B");
            Thread t3 = new Thread(new PrintABC(t2),"C");
            t1.start();
            t2.start();
            t3.start();
            Thread.sleep(1000);
        }
    }

    /**
     *join方式
     */

    public class PrintABC implements Runnable{
        private Thread beforeThread;

        public PrintABC(Thread beforeThread) {
            this.beforeThread = beforeThread;
        }
        @Override
        public void run() {
            if (beforeThread!=null){
                try {
                    beforeThread.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                System.out.println(Thread.currentThread().getName());
            }else {
                System.out.println(Thread.currentThread().getName());
            }
        }
    }

    /**
     * 基于synchronized的方式
     */
    @Test
    public void test1(){
        WaitNotify waitNotify = new WaitNotify();
        while(true){
            new Thread(()->{
                waitNotify.run(0);
            },"A").start();
            new Thread(()->{
                waitNotify.run(1);
            },"B").start();
            new Thread(()->{
                waitNotify.run(2);
            },"C").start();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public class WaitNotify{
        private int number=0;

        private Object lock=new Object();
        public void run(int target){
            synchronized (lock){
                if (number%3!=target){
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                number++;
                System.out.println(Thread.currentThread().getName());
                lock.notifyAll();
            }
        }
    }

    /**
     * 线程池的方法
     */
    @Test
    public void test2(){
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        ThreadPool threadPool = new ThreadPool();
        int index=0;
        while (true){
            int finalIndex = index;
            executorService.execute(()->{
                threadPool.run(finalIndex);
            });
            index++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
    public  class ThreadPool{
        Object object=new Object();
        String str="ABC";
        public void run(int target){
            char[] chars = str.toCharArray();
            System.out.println(Thread.currentThread().getName()+" : "+chars[target%3]);
        }
    }
}
