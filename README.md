需求分析
1. 智能分析：用户输入目标和原始数据（图表类型），自动生成图表和分析结论
2. 图表管理
3. 图表生成的异步化（消息队列）
4. 对接 AI 能力
技术栈
后端
1. Spring Boot 
2. MySQL数据库
3. MyBatis Plus数据访问框架
4. 消息队列（RabbitMQ)
5. AI 能力（OpenAI  接口开发 /其他AI接口）
6.  Excel 的上传 和数据的解析（Easy Excel)
7. Swagger + Knife4j 项目接口文档
8. Hutool 工具库
前端
1. React
2. Umi + Ant design Pro
3. 可视化开发库（Echarts + HighCharts + AntV)
4. umi openapi 代码生成（自动生成后端调用代码）
后端基础开发
自动生成后端增删改查代码：
1. 执行SQL语句建表
2. mybatisX插件生成代码
3. 复制老的增删改查模板，根据表重构
4. 根据接口文档测试接口的可用性
前端基础开发
1. 使用脚手架进行搭建，实现基础代码
2. 修改官方初始化的报错
！遇到了 BUG 根据github issues 区，成功找到解决方案，并解决了移除国际化的报错
https://github.com/ant-design/ant-design-pro/issues/10452
上面介绍了国际化报错的解决方案
3. 开发基本的登录注册功能
智能分析业务开发
业务流程
1. 用户输入
  a. 分析目标
  b. 上传原始数据（excel）
  c. 更精细话控制图表：比如图表类型、图表名称等
2. 后端校验
  a. 校验用户的输入是否合法（长度等）
  b. 成本控制（次数校验和统计、鉴权）
3. 把处理后的数据输入给AI模型（调用AI），提供图表信息、结论文本
4. 图表信息、结论文本在前端进行展示
开发接口
根据输入的文件和文本，最后返回图表信息和结论文本
数据压缩（降低提问成本，AI都有字数限制）
使用 csv 对 excel 文件的数据进行提取和压缩
使用Easy Excel https://easyexcel.opensource.alibaba.com/docs/current/ 对excel文件进行读取

    /**
     * excel 转 csv
     *
     * @param multipartFile
     * @return
     */
    public static String excelToCsv(MultipartFile multipartFile) {
        // 读取数据
        List<Map<Integer, String>> list = null;
        try {
            list = EasyExcel.read(multipartFile.getInputStream())
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet()
                    .headRowNumber(0)
                    .doReadSync();
        } catch (IOException e) {
            log.error("表格处理错误", e);
        }
        if (CollUtil.isEmpty(list)) {
            return "";
        }
        // 转换为 csv
        StringBuilder stringBuilder = new StringBuilder();
        // 读取表头
        LinkedHashMap<Integer, String> headerMap = (LinkedHashMap) list.get(0);
        List<String> headerList = headerMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
        stringBuilder.append(StringUtils.join(headerList, ",")).append("\n");
        // 读取数据
        for (int i = 1; i < list.size(); i++) {
            LinkedHashMap<Integer, String> dataMap = (LinkedHashMap) list.get(i);
            List<String> dataList = dataMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
            stringBuilder.append(StringUtils.join(dataList, ",")).append("\n");
        }
        return stringBuilder.toString();
    }
调用AI
● openai：成本较高，需要魔法和要钱
  ○ 这里可以使用http请求的方法，需要钱购买key，通过http请求方式实现对接AI接口
使用Java代码实现，可以使用Hutool 工具类、httpClient 等发送http请求的方式根据下面的更改即可实现对接AI接口
curl "https://api.openai.com/v1/chat/completions" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $OPENAI_API_KEY" \
    -d '{
        "model": "gpt-4o-mini",
        "messages": [
            {
                "role": "system",
                "content": "You are a helpful assistant."
            },
            {
                "role": "user",
                "content": "Write a haiku that explains the concept of recursion."
            }
        ]
    }'


使用讯飞星火AI模型
星火认知大模型Http调用文档
1. 使用Hutool工具库中的HttpRequest发送请求
2. 向AI提供提供它的身份以及详细要求，为了更好的获得固定的数据结构
3. 拿到响应值后通过Hutool工具库解析Json，并提取Content
4. 保存到数据库中
代码实现
1. 创建XFApiManager调用第三方API文档，传入需求，选择调用4.0Ultra，以及自己的API_KEY发送Http请求
请求实例：
curl -i -k -X POST 'https://spark-api-open.xf-yun.com/v1/chat/completions' \
--header 'Authorization: Bearer 123456' \
--header 'Content-Type: application/json' \
--data '{
    "model":"generalv3.5",
    "messages": [
        {
            "role": "user",
            "content": "来一个只有程序员能听懂的笑话"
        }
    ],
    "stream": true
}'
根据上面请求使用Hutool工具库发送http请求：
package com.yupi.springbootinit.manager;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import com.yupi.springbootinit.model.dto.chart.Message;
import com.yupi.springbootinit.model.dto.chart.XFApiRequestBody;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;



    @Slf4j
    @Service
    public class XFApiManager  {
    private static final String API_URL = "https://spark-api-open.xf-yun.com/v1/chat/completions";
    private static final String API_KEY = "BzNDMhirjmcBTZAKJpVO:YeqbfZcynMcCzUJhnilS";  // 使用实际的API密钥
    private static final String SYSTEM = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
            "分析需求：\n" +
            "{数据分析的需求或者目标}\n" +
            "原始数据：\n" +
            "{csv格式的原始数据，用,作为分隔符}\n" +
            "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
            "【【【【【\n" +
            "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
            "【【【【【\n" +
            "{明确的数据分析结论、越详细越好，不要生成多余的注释}\n";  // 使用实际的API密钥



    public String sendRequest(String content) {
        // 构建请求体
        String jsonBody = JSONUtil.toJsonStr(new XFApiRequestBody(
            "4.0Ultra",
            "1",
            new Message[]{
                    new Message("system", SYSTEM),

                    new Message("user", content),

            }

        ));

        // 发送 POST 请求
        HttpResponse response = HttpRequest.post(API_URL)
            .header("Authorization", "Bearer " + API_KEY)
            .header("Content-Type", "application/json")
            .body(jsonBody)
            .execute();

        log.info(response.toString());

        // 返回响应内容
        // 获取响应的 body
        String responseBody = response.body();

        // 使用 JSONUtil 解析响应体
        JSONObject responseJson = JSONUtil.parseObj(responseBody);

        // 从 "choices" 数组中提取第一个元素，并获取 "content" 字段
        String contentValue = responseJson.getJSONArray("choices")
                .getJSONObject(0)  // 获取第一个元素
                .getJSONObject("message")  // 获取 "message" 对象
                .getStr("content");  // 获取 "content" 字段

        return contentValue;
    }

}

package com.yupi.springbootinit.model.dto.chart;

import lombok.Data;

// 请求体数据结构
@Data
public class XFApiRequestBody {
    private String model;
    private String user;
    private Message[] messages;


    // 构造方法
    public XFApiRequestBody(String model, String user, Message[] messages) {
        this.model = model;
        this.user = user;
        this.messages = messages;
    }
}
package com.yupi.springbootinit.model.dto.chart;

import lombok.Data;

// 消息结构
@Data
    public  class Message {
        private String role;
        private String content;

        // 构造方法
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
2. 使用第三方API，拿到返回值，更新到数据库中，传入用户输入的需求
String result = xfApiManager.sendRequest(userInput.toString());
返回值响应体：需要对其解析

        // 返回响应内容
        // 获取响应的 body
        String responseBody = response.body();

        // 使用 JSONUtil 解析响应体
        JSONObject responseJson = JSONUtil.parseObj(responseBody);

        // 从 "choices" 数组中提取第一个元素，并获取 "content" 字段
        String contentValue = responseJson.getJSONArray("choices")
                .getJSONObject(0)  // 获取第一个元素
                .getJSONObject("message")  // 获取 "message" 对象
                .getStr("content");  // 获取 "content" 字段


响应值到Echarts中，将得到的数据正确展示出来
https://echarts.apache.org/examples/zh/editor.html?c=line-simple


生成图表
利用前端的组件库（Echarts）进行展示
在线调试：
https://echarts.apache.org/examples/zh/index.html

思考：
1. 安全性：
  a. 上传文件过大？
  b. 疯狂点击提交？
  c. AI生成太慢，许多用户同时生成，给系统造成压力，如何提高体验和系统可用性

收获
1. 学会阅读API文档，通过文档介绍，实现使用Http请求调用第三方API
2. 学会使用AI生成固定格式的数据，尽量保证生成的格式能够统一，进行后续的处理


12月03日
1. 创建联合索引，优化历年录取院校以及专业查询，将接口查询时间从2秒多降低到1秒内，优化用户体验，提高查询速度
12月04日
分库分表
● 水平分表
将一张表中的数据按一定规则划分到不同的物理存储位置中
● 垂直分库
根据业务模块不同，将不同的字段或者表，分到不同的数据库中，

限流
问题：使用系统是需要消耗成本的，用户有可能疯狂刷量，让你破产
解决问题：
1. 控制成本 => 限制用户调用总次数
2. 用户短时间疯狂使用，服务器被占满，其他用户无法使用 => 限流

思考限流阈值多大合适？参考正常用户的使用，比如限制单个用户在每秒智能使用1次
限流的几种算法
1. 固定窗口限流
单位时间内允许部分操作
例如：一小时只允许10个用户操作
优点: 最简单
缺点：可能出现流量突刺
例如：前59分钟没有1个操作，第59分钟来了10个操作；第1小时01分钟又来了10个操作。相当于2分钟内执行了20个操作，服务器仍然有高峰危险。
2. 滑动窗口限流
单位时间内允许部分操作，但是这个单位时间是滑动的，需要指定一个滑动单位
比如滑动单位1min
开始前
0s   1h   2h
1分钟后
1min  1h 1min
这个时间是滑动的

优点：能够解决上述流量突刺的问题
缺点：实现相对复杂，限流效果和滑动单位有关，滑动单位越小，限流效果越好，但是很难选到一个特别合适的滑动单位

3. 漏桶限流（这里可以想象成一个漏水的桶，每秒漏1滴水）（推荐）
以固定的速率处理请求（漏水），当请求桶满了后，拒绝请求
每秒处理10个请求，桶的容量是10，每0.1秒固定处理一次请求，如果1秒内来了10个请求，都可以处理完，但是1秒内来了11个请求，最后那个请求就会溢出桶，被拒绝
优点：能够一定程度上应对流量突刺
缺点：没有办法迅速处理一批请求，智能一个一个按顺序来处理（固定速率的缺点）
4. 令牌桶限流（推荐）
 	管理员先生成一批令牌，每秒生成10个令牌；用户操作前，先去拿到一个令牌，有令牌的人就有资格去执行操作，能同时执行操作；拿不到令牌的就等着
优点：能够并发处理同时的请求，并发性能会更高
需要考虑的问题：还是存在时间单位选取的问题

限流粒度
1. 针对某个方法限流，单位时间内最多允许同时XX 个操作使用这个方法
2. 针对某个用户限流，比如单个用户单位时间内最多执行XX次操作
3. 针对某个用户X方法限流，比如单个用户单位时间内最多执行XX次这个方法
限流的实现
1）本地限流（单机限流）
Guava RateLimiter
一般使用第三方库实现，单体项目比较适合

2）分布式限流（多机限流）
Redisson 内置了一个限流工具类，帮助你利用Redis来存储、统计
https://github.com/redisson/redisson

1. 引入代码包后，创建RedissonConfig 配置类，用于初始化RedissonClient 对象单例
@Configuration
@ConfigurationProperties(prefix = "spring.redis")
@Data
public class RedissonConfig {

    private Integer database;

    private String host;

    private Integer port;

    private String password;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
        .setDatabase(database)
        .setAddress("redis://" + host + ":" + port);
        RedissonClient redisson = Redisson.create(config);
        return redisson;
    }
}

2. 编写RedisLimiterManager 
什么是Manager？专门提供RedisLimiter 限流基础服务的（提供了通用能力，可以放到任何一个项目中）
/**
 * 专门提供 RedisLimiter 限流基础服务的（提供了通用的能力）
 */
@Service
public class RedisLimiterManager {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 限流操作
     *
     * @param key 区分不同的限流器，比如不同的用户 id 应该分别统计
     */
    public void doRateLimit(String key) {
        // 创建一个名称为user_limiter的限流器，每秒最多访问 2 次
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.trySetRate(RateType.OVERALL, 2, 1, RateIntervalUnit.SECONDS);
        // 每当一个操作来了后，请求一个令牌
        boolean canOp = rateLimiter.tryAcquire(1);
        if (!canOp) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST);
        }
    }
}

3. 应用到限流方法中，比如智能分析接口
User loginUser = userService.getLoginUser(request);
// 限流判断，每个用户一个限流器
redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());
// 后续操作
xxxxx


业务流程分析
标准异步化的业务流程
1. 当用户要进行耗时很长的操作时，点击提交后，不需要在界面等，而是应该把这个任务保存到数据库中记录下来
2. 用户要执行新任务时：
  a. 任务提交成功：
    ⅰ. 如果程序还有多余空闲线程，可以立刻去做这个任务
    ⅱ. 如果我们的程序的线程都在繁忙，无法继续处理，那就放到等待队列中
  b. 任务提交失败：比如我们的程序所有线程都在忙，任务队列满
    ⅰ. 拒绝掉这个任务，再也不去执行
    ⅱ. 通过保存到数据库中的记录，看到提交失败的任务，并且在程序空闲的时候，把任务从数据库中捞到程序里，再去执行
3. 程序（线程）从任务队列中取出任务依次执行，每完成一件事情，修改任务状态
4. 用户可以查询到任务的执行状态，或者在任务执行成功，或者失败时能得到通知（发邮件、系统消息提示，短信），从而优化体验
5. 如果我们执行任务复杂，包含很多环节，在每一个小任务完成时，要在程序（数据库中）记录一个任务的执行状态（进度）

系统的业务流程
1. 用户点击智能分析页的提交按钮，先把图表立刻保存到数据库中（作为一个任务）
2. 用户可以点击图表管理页面查看所有图表（已经生成的、生成中的、生成失败的）信息和状态
3. 用户可以修改生成失败的图表信息，点击重新生成
优化流程（异步化）


问题：
1. 任务队列的最大容量
2. 怎么从任务队列中取出任务去执行？任务队列的流程怎么实现？怎么保证程序最多同时执行多个任务？

问题解决：线程池

为什么需要线程池？（自己被问到过)
1、线程管理比较复杂（比如什么时候新增线程、什么时候减小空闲线程）
2、任务存取比较复杂（什么时候接受任务、什么时候拒绝任务）

线程池的作用：帮助轻松管理线程、协调任务的执行过程。 

线程池的实现
1. Spring中提供了 ThreadPoolTaskExecutor配合 @Async 来实现 （不推荐）
2. Java中提供了ThreadPoolExecutor来实现 （灵活 ，推荐使用）自定义线程池

线程池参数：
线程池可以理解为一家公司，线程是员工，任务是工作。
corePoolSize=2代表公司有两名正式员工。
当有第一个工作时候，会直接分配给这里的正式员工；
当有第二份工作时候，这时候另一个正式员工会做这份工作；
当有第三份工作时候，会将工作列入清单（workQueue工作队列）中，等待其他工作执行完，去这个清单中按顺序取出来去执行（这里就是加入任务队列中，其他线程执行任务结束就会去队列中取任务执行）；
直到队列满了，在有多余的一份工作时候，代表公司人手不够了，公司就会招临时工，去做工作（可招多少临时工取决于maximumPoolSize-corePoolSize的数量）临时工会去做队列外多余的工作，而不是做清单中的工作，因为已经分配给了正式员工；
直到临时工也都有工作了，这时候又来了新工作，这时候公司没有资金招新员工，默认就会拒绝掉这份工作（这个RejectedExecutionHandler策略可以自定义，比如可以选择保存到数据库中，等待其他工作执行完再去取这个任务执行，默认是直接拒绝掉）
当工作都结束后，临时工也做完了，老板就会将其开了，防止浪费资金（浪费资源）keepAliveTime这个就是让它在公司摸多久时间鱼
public ThreadPoolExecutor(int corePoolSize,
                          int maximumPoolSize,
                          long keepAliveTime,
                          TimeUnit unit,
                          BlockingQueue<Runnable> workQueue,
                          ThreadFactory threadFactory,
                          RejectedExecutionHandler handler) {

线程池的参数如何设置？
现有条件：比如AI生成能力的并发是只允许4个任务同时去执行，AI能力允许20个任务排队
corePoolSize( 核心线程数 => 正式员工数）：正常情况下，可以设置2-4
maximumPoolSize：设置为极限情况，设置为<=4
keepAliveTime（空闲线程存活时间）：一般设置为秒级或者分钟级
TimeUnit unit（空闲线程存活时间的单位）：分钟、秒
workQueue（工作队列）：结合实际请况去设置，可以设置为 20
threadFactory（线程工厂）：控制每个线程的生成、线程的属性（比如线程名）
RejectedExecutionHandler（拒绝策略）：抛异常，标记数据库的任务状态为 “任务满了已拒绝”
一般情况下，任务分为 IO 密集型和计算密集型两种。
计算密集型： 吃 CPU ，比如音视频处理、图像处理、数学计算等，一般是设置 corePoolSize 为 CPU 的核数 + 1（空余线程），可以让每个线程都能利用好 CPU 的每个核，而且线程之间不用频繁切换（减少打架、减少开销）
IO 密集型： 吃带宽/内存/硬盘的读写资源 ，corePoolSize 可以设置大一点，一般经验值是 2n 左右，但是建议以 IO 的能力为主。

代码实现
自定义线程池：
@Configuration
public class ThreadPoolExecutorConfig {

    @Bean
    public ThreadPoolExecutor threadPoolExecutor() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private int count = 1;

            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("线程" + count);
                count++;
                return thread;
            }
        };
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(2, 4, 100, TimeUnit.SECONDS,
                                                                       new ArrayBlockingQueue<>(4), threadFactory);
        return threadPoolExecutor;
    }
}

提交任务到线程池：CompletableFuture用法
CompletableFuture.runAsync(() -> {
    System.out.println("任务执行中：" + name + "，执行人：" + Thread.currentThread().getName());
    try {
        Thread.sleep(60000);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
}, threadPoolExecutor);

测试：
package com.yupi.springbootinit.controller;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 队列测试
 *
 */
@RestController
@RequestMapping("/queue")
@Slf4j
public class QueueController {

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @GetMapping("/add")
    public void add(String name) {
        CompletableFuture.runAsync(() -> {
            log.info("任务执行中：" + name + "，执行人：" + Thread.currentThread().getName());
            try {
                Thread.sleep(600000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, threadPoolExecutor);
    }

    @GetMapping("/get")
    public String get() {
        Map<String, Object> map = new HashMap<>();
        int size = threadPoolExecutor.getQueue().size();
        map.put("队列长度", size);
        long taskCount = threadPoolExecutor.getTaskCount();
        map.put("任务总数", taskCount);
        long completedTaskCount = threadPoolExecutor.getCompletedTaskCount();
        map.put("已完成任务数", completedTaskCount);
        int activeCount = threadPoolExecutor.getActiveCount();
        map.put("正在工作的线程数", activeCount);
        return JSONUtil.toJsonStr(map);
    }
}


任务1：正式员工1执行了

任务2：正式员工2执行了

任务3：加入清单了（加入任务队列中）
任务4：加入清单了（加入任务队列中）

任务5、6：加入清单了（加入任务队列中）

任务7：公司人手不够请了临时工1（可招多少临时工取决于maximumPoolSize-corePoolSize的数量)
任务8：公司人手不够请了临时工2（可招多少临时工取决于maximumPoolSize-corePoolSize的数量)

任务9：公司资金不够，无法请多余的临时工，将任务拒绝掉（这里时默认的拒绝策略RejectedExecutionException）
实现工作流程
1. 给 chart 表新增任务状态字段（比如排队中、执行中、已完成、失败），任务执行信息字段（用于记录任务执行中、或者失败的一些信息）
2. 用户点击智能分析页的提交按钮时，先把图表立刻保存到数据库中，然后提交任务
3. 任务：先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果；执行失败后，状态修改为 “失败”，记录任务失败信息。
4. 用户可以在图表管理页面查看所有图表（已生成的、生成中的、生成失败）的信息和状态
用户可以修改生成失败的图表信息，点击重新生成
库表设计
chart 表新增字段：
▼sql

复制代码status       varchar(128) not null default 'wait' comment 'wait,running,succeed,failed',
execMessage  text   null comment '执行信息',
任务执行逻辑
先修改任务状态为执行中，减少重复执行的风险、同时让用户知道执行状态。
注意异常处理：
▼java

复制代码CompletableFuture.runAsync(() -> {
    // 先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果；执行失败后，状态修改为 “失败”，记录任务失败信息。
    Chart updateChart = new Chart();
    updateChart.setId(chart.getId());
    updateChart.setStatus("running");
    boolean b = chartService.updateById(updateChart);
    if (!b) {
        handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
        return;
    }
    // 调用 AI
    String result = aiManager.doChat(biModelId, userInput.toString());
    String[] splits = result.split("【【【【【");
    if (splits.length < 3) {
        handleChartUpdateError(chart.getId(), "AI 生成错误");
        return;
    }
    String genChart = splits[1].trim();
    String genResult = splits[2].trim();
    Chart updateChartResult = new Chart();
    updateChartResult.setId(chart.getId());
    updateChartResult.setGenChart(genChart);
    updateChartResult.setGenResult(genResult);
    updateChartResult.setStatus("succeed");
    boolean updateResult = chartService.updateById(updateChartResult);
    if (!updateResult) {
        handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
    }
}, threadPoolExecutor);


介绍
 虽然 CompletableFuture 本身并不完全符合传统意义上的工厂类（因为它不只是提供实例化的功能），但它确实提供了多个工厂方法来创建和管理异步任务。因此，可以认为 CompletableFuture 具有工厂类的特征，特别是在它用来创建异步任务并管理任务状态的功能方面。  
提交任务
CompletableFuture 是 Java 8 引入的一个非常强大的异步编程工具，它不仅可以用于并发执行任务，还提供了非常丰富的 API 来处理异步任务的结果，支持非阻塞的任务执行和多任务组合。
在 CompletableFuture 中，可以通过 supplyAsync() 或 runAsync() 方法提交任务到线程池。runAsync() 用于提交没有返回值的任务（Runnable），而 supplyAsync() 用于提交有返回值的任务（Supplier）。
下面是一些使用 CompletableFuture 提交任务的例子：
1. 使用 runAsync() 提交没有返回值的任务
runAsync() 用于提交 Runnable 任务，这些任务没有返回值。
示例：
import java.util.concurrent.*;

public class CompletableFutureExample {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // 创建自定义线程池
        Executor executor = Executors.newFixedThreadPool(4);

        // 提交没有返回值的任务
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                System.out.println("Task is running on thread: " + Thread.currentThread().getName());
                Thread.sleep(1000);  // 模拟任务执行
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, executor);

        // 等待任务执行完成
        future.get();

        System.out.println("Task completed");

        // 关闭线程池
        ((ExecutorService) executor).shutdown();
    }
}
● runAsync(): 提交一个无返回值的任务（Runnable）。它返回一个 CompletableFuture<Void> 对象，用于跟踪任务的完成。
2. 使用 supplyAsync() 提交有返回值的任务
CompletableFuture.supplyAsync() 用于提交一个 Supplier 任务，它返回一个结果，因此你可以通过 CompletableFuture 来获取结果。
示例：
import java.util.concurrent.*;

public class CompletableFutureExample {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // 创建自定义线程池
        Executor executor = Executors.newFixedThreadPool(4);

        // 提交一个有返回值的任务
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Task is running on thread: " + Thread.currentThread().getName());
                Thread.sleep(1000);  // 模拟任务执行
                return "Result from task";
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }
        }, executor);

        // 获取任务结果
        String result = future.get();
        System.out.println("Task result: " + result);

        // 关闭线程池
        ((ExecutorService) executor).shutdown();
    }
}
● supplyAsync(): 提交一个有返回值的任务（Supplier）。它返回一个 CompletableFuture<T> 对象，可以通过 future.get() 获取任务的执行结果。
3. 使用 thenApply() 链式处理结果
你可以使用 thenApply() 方法在任务完成后对结果进行处理，thenApply() 会返回一个新的 CompletableFuture，可以继续进行链式操作。
示例：
import java.util.concurrent.*;

public class CompletableFutureExample {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // 创建自定义线程池
        Executor executor = Executors.newFixedThreadPool(4);

        // 提交一个有返回值的任务
        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Task is running on thread: " + Thread.currentThread().getName());
                Thread.sleep(1000);
                return 5; // 返回整数值
            } catch (InterruptedException e) {
                e.printStackTrace();
                return 0;
            }
        }, executor);

        // 使用 thenApply 对结果进行处理
        CompletableFuture<Integer> resultFuture = future.thenApply(result -> {
            System.out.println("Processing result: " + result);
            return result * 2;  // 将结果乘以 2
        });

        // 获取最终结果
        Integer finalResult = resultFuture.get();
        System.out.println("Final result: " + finalResult);

        // 关闭线程池
        ((ExecutorService) executor).shutdown();
    }
}
● thenApply(): 用于对 CompletableFuture 的结果进行转换。返回一个新的 CompletableFuture，在该 CompletableFuture 完成后会继续处理下一个任务。
4. 使用 thenAccept() 不返回结果
如果你不需要处理返回值，只需要做一些副作用操作（例如打印、日志记录等），可以使用 thenAccept()。
示例：
import java.util.concurrent.*;

public class CompletableFutureExample {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // 创建自定义线程池
        Executor executor = Executors.newFixedThreadPool(4);

        // 提交一个有返回值的任务
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Task is running on thread: " + Thread.currentThread().getName());
                Thread.sleep(1000);
                return "Task completed";
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }
        }, executor);

        // 使用 thenAccept 不返回结果
        future.thenAccept(result -> {
            System.out.println("Received result: " + result);
        });

        // 由于 thenAccept 不返回结果，因此没有 `future.get()`，所以直接退出
        Thread.sleep(2000);  // 等待任务执行完

        // 关闭线程池
        ((ExecutorService) executor).shutdown();
    }
}
● thenAccept(): 用于在任务完成后消费结果，但不会返回新的 CompletableFuture。
5. 组合多个 CompletableFuture
CompletableFuture 支持多个任务的组合，你可以使用 thenCombine() 来合并两个任务的结果，或者使用 allOf() 和 anyOf() 来等待多个任务的完成。
示例：thenCombine()
import java.util.concurrent.*;

public class CompletableFutureExample {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // 创建自定义线程池
        Executor executor = Executors.newFixedThreadPool(4);

        // 提交两个任务，任务返回两个不同的结果
        CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Task 1 is running on thread: " + Thread.currentThread().getName());
                Thread.sleep(1000);
                return 5;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return 0;
            }
        }, executor);

        CompletableFuture<Integer> future2 = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Task 2 is running on thread: " + Thread.currentThread().getName());
                Thread.sleep(500);
                return 3;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return 0;
            }
        }, executor);

        // 合并两个任务的结果
        CompletableFuture<Integer> combinedFuture = future1.thenCombine(future2, (result1, result2) -> {
            return result1 + result2;  // 合并结果
        });

        // 获取合并后的结果
        Integer combinedResult = combinedFuture.get();
        System.out.println("Combined result: " + combinedResult);

        // 关闭线程池
        ((ExecutorService) executor).shutdown();
    }
}
● thenCombine(): 用于合并两个独立的 CompletableFuture 的结果，返回一个新的 CompletableFuture。
总结
● runAsync(): 提交一个无返回值的 Runnable 任务。
● supplyAsync(): 提交一个有返回值的 Supplier 任务。
● thenApply(): 对 CompletableFuture 的结果进行转换，返回一个新的 CompletableFuture。
● thenAccept(): 消费 CompletableFuture 的结果，不返回新的 CompletableFuture。
● thenCombine(): 合并两个 CompletableFuture 的结果。
CompletableFuture 提供了很多方法，可以让你在异步执行时更加灵活地处理任务和组合多个任务的执行结果。
并发执行任务
CompletableFuture 进行并发任务执行的关键是它支持异步执行，意味着它会在后台线程池中异步地运行任务。它本质上是对异步编程的一种封装，支持非阻塞的操作，从而让多个任务并发执行并且互不阻塞。
如何并发执行任务？
1. 异步执行：使用 CompletableFuture.supplyAsync() 或 CompletableFuture.runAsync() 方法提交任务，这些方法会将任务提交到线程池中执行，而不会阻塞当前线程。
2. 后台线程池：CompletableFuture 会使用提供的线程池来执行任务。如果没有提供线程池，它会使用默认的公共线程池（ForkJoinPool.commonPool()）。可以通过传入自定义的 Executor 来指定线程池。
3. 非阻塞：CompletableFuture 会立即返回，而不是等待任务执行完成。这就意味着多个任务可以并发执行，而且可以使用链式调用来在任务完成后处理结果。
示例：并发执行多个任务
假设我们需要并发执行多个任务，并在所有任务完成后对结果进行处理。
1. 使用 supplyAsync() 执行多个异步任务
import java.util.concurrent.*;

public class CompletableFutureConcurrentExample {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // 创建自定义线程池
        Executor executor = Executors.newFixedThreadPool(4);

        // 提交多个异步任务并返回各自的结果
        CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Task 1 is running on thread: " + Thread.currentThread().getName());
                Thread.sleep(1000);  // 模拟任务执行
                return 5;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return 0;
            }
        }, executor);

        CompletableFuture<Integer> future2 = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Task 2 is running on thread: " + Thread.currentThread().getName());
                Thread.sleep(500);  // 模拟任务执行
                return 3;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return 0;
            }
        }, executor);

        CompletableFuture<Integer> future3 = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Task 3 is running on thread: " + Thread.currentThread().getName());
                Thread.sleep(1500);  // 模拟任务执行
                return 7;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return 0;
            }
        }, executor);

        // 等待所有任务执行完成并合并结果
        CompletableFuture<Integer> combinedFuture = CompletableFuture.allOf(future1, future2, future3)
            .thenApply(v -> future1.join() + future2.join() + future3.join());

        // 获取最终结果
        Integer result = combinedFuture.get();
        System.out.println("Combined result: " + result);

        // 关闭线程池
        ((ExecutorService) executor).shutdown();
    }
}
代码解释
1. 提交多个异步任务： 
  ○ 我们使用 CompletableFuture.supplyAsync() 来提交 3 个异步任务（future1, future2, future3），这些任务分别在不同的线程中执行。
  ○ 每个任务执行时，都会打印当前线程的名称，并模拟任务的执行时间（通过 Thread.sleep() 来模拟延迟）。
2. allOf() 方法： 
  ○ CompletableFuture.allOf(future1, future2, future3) 会返回一个新的 CompletableFuture，表示当 future1, future2, future3 都完成时，这个新的 CompletableFuture 也完成。
  ○ 使用 thenApply() 来合并这些任务的结果。join() 方法确保获取每个任务的返回值。
3. 并发执行：
  ○ 由于每个任务都是异步执行的，它们会并发地在各自的线程中执行，而不会互相阻塞。
  ○ Thread.sleep() 模拟了任务的耗时，future2 的任务比 future1 和 future3 执行得更快，整个程序会在最慢的任务完成时才继续执行。
4. 获取结果：
  ○ get() 会阻塞当前线程，直到所有任务完成并合并结果。
  ○ join() 方法用于获取每个 CompletableFuture 的结果，join() 不会抛出异常，而是返回计算结果。如果任务发生异常，join() 会返回一个默认值。
2. 通过 thenCombine() 合并两个任务的结果
如果你有两个并发执行的任务，且它们的结果需要合并，可以使用 thenCombine()。它允许你在两个 CompletableFuture 完成后合并它们的结果。
import java.util.concurrent.*;

public class CompletableFutureCombineExample {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // 创建自定义线程池
        Executor executor = Executors.newFixedThreadPool(4);

        // 提交两个并发任务
        CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Task 1 is running on thread: " + Thread.currentThread().getName());
                Thread.sleep(1000);
                return 5;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return 0;
            }
        }, executor);

        CompletableFuture<Integer> future2 = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Task 2 is running on thread: " + Thread.currentThread().getName());
                Thread.sleep(500);
                return 3;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return 0;
            }
        }, executor);

        // 使用 thenCombine 合并两个任务的结果
        CompletableFuture<Integer> combinedFuture = future1.thenCombine(future2, (result1, result2) -> {
            return result1 + result2;  // 合并两个结果
        });

        // 获取结果
        Integer result = combinedFuture.get();
        System.out.println("Combined result: " + result);

        // 关闭线程池
        ((ExecutorService) executor).shutdown();
    }
}
代码解释
1. thenCombine()：  
  ○ thenCombine() 接受两个 CompletableFuture 和一个合并函数，合并这两个任务的结果。
  ○ 当 future1 和 future2 都完成时，合并函数会被调用，它会将两个任务的结果合并在一起。
2. 并发执行：  
  ○ 由于 future1 和 future2 是异步执行的，它们会在不同的线程中并发运行，而不会互相阻塞。
3. 通过 anyOf() 等待任意任务完成
CompletableFuture.anyOf() 方法可以用来在多个任务中等待第一个完成的任务。当第一个任务完成时，就会返回对应的结果。
import java.util.concurrent.*;

public class CompletableFutureAnyOfExample {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // 创建自定义线程池
        Executor executor = Executors.newFixedThreadPool(4);

        // 提交多个并发任务
        CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Task 1 is running on thread: " + Thread.currentThread().getName());
                Thread.sleep(1000);
                return 5;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return 0;
            }
        }, executor);

        CompletableFuture<Integer> future2 = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Task 2 is running on thread: " + Thread.currentThread().getName());
                Thread.sleep(500);  // 模拟较短的执行时间
                return 3;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return 0;
            }
        }, executor);

        // 使用 anyOf 等待第一个完成的任务
        CompletableFuture<Object> anyOfFuture = CompletableFuture.anyOf(future1, future2);

        // 获取第一个完成的任务的结果
        Object result = anyOfFuture.get();
        System.out.println("First completed task result: " + result);

        // 关闭线程池
        ((ExecutorService) executor).shutdown();
    }
}
代码解释
1. anyOf()：  
  ○ CompletableFuture.anyOf() 等待多个任务中的任意一个任务完成，返回第一个完成任务的结果。
  ○ 任务的执行顺序和结果取决于哪个任务先完成，不会等待其他任务。
2. 并发执行：  
  ○ future1 和 future2 并发执行，anyOf() 只会等待第一个完成的任务。
总结
● 并发执行：CompletableFuture 提交任务到线程
池中异步执行，多个任务可以并发执行。
● 线程池：CompletableFuture 使用线程池来执行任务，可以通过传递自定义 Executor 来指定线程池。
● 任务组合：通过 allOf(), thenCombine(), anyOf() 等方法可以组合多个任务并进行结果处理。
● 非阻塞：CompletableFuture 不会阻塞主线程，可以并发执行多个任务。

为什么要用消息队列？
1. 异步处理
生产者发送完消息之后，可以忙别的，消费者想什么时候消费都可以，不会产生阻塞
2. 削峰
先把用户请求放到消息队列中，消费者可以按照自己的需求，慢慢去取出来处理请求
原本：某一时刻10万多个请求同时来了，需要立刻处理，很快压力过大
现在，这些请求放到消息队列中，处理系统以自己的恒定速率慢慢执行，保护系统，稳定处理
3. 解耦
在多个不同的系统、应用之间实现消息的传输（也可以存储）。不需要考虑传输应用的编程语言、系统、框架等。
比如：可以让java开发的应用发消息，让go开发的应用接受消息，这样就可以不用把所有代码写进一个项目中从而实现应用解耦
消息队列的模型
生产者：Producer
消费者：Consumer
消息：Message
消息队列：Queue

分布式消息队列的优势
1. 数据持久化：它可以把消息集中存储到硬盘中，服务器重启就不会丢失
2. 可扩展性：可以根据需求，随时增加节点，继续保持稳定的服务
3. 应用解耦：可以连接各个语言、框架开发的系统，让这些系统能够灵活传输读取数据
应用解耦的优点
  a. 一个系统挂了，不影响另一个系统
  b. 系统挂了并恢复，仍然可以取出消息，继续执行业务逻辑
  c. 只要发送消息到队列，就可以立刻返回，不用同步调用所有系统，性能更高

4. 发布订阅


应用场景
1. 耗时场景（异步）
2. 高并发（异步，削峰填谷）
3. 分布式系统协作（跨团队、跨业务协作、应用解耦）
4. 强稳定性的场景（比如金融业务，持久化，可靠性、削峰填谷）

消息队列的缺点
额外引入额外的中间件，系统会更复杂、额外维护中间件、额外的费用（部署）成本
消息队列：消息丢失、顺序性、重复消费、数据一致性（分布式系统就要考虑）

主流分布式消息队列选型
1. activemq
2. rabbitmq
3. kafka
4. rocketmq
5. pulsar


技术对比
● 吞吐量：IO、并发
● 时效性：类似延迟，消息的发送、到达时间
● 可用性：系统可用的比率（比如1年宕机1s，可用率999好多个%）
● 可靠性：消息不丢失（比如不丢失订单），功能正常完成
技术选型	吞吐量	时效性	可用性	可靠性	优势	应用场景
activemq	万级	高	高	高	简单易学	中小型企业、项目
rabbitmq	万级	极高（微秒）	高	高	生态好（基本什么语言都支持）时效性高、易学	适合绝大多数分布式应用
（推荐学的原因）
kafka	十万级	高	极高	极高	吞吐量大、可靠性、可用性，强大的数据流处理能力	适用于大规模处理数据的场景，比如构建日志收集系统、实时数据流传输、事件流收集传输
rocketmq	十万级	高	极高	极高	吞吐量大、可靠性、可用性、可扩展性	适用于金融、电商等可靠性要求较高的场景，适合大规模的消息处理。
pulsar	十万级	高	极高	极高	可靠性、可用性很高，基于发布订阅模型，新兴（技术架构先进）	适合大规模 、高并发的分布式系统（云原生）。适合实时分析、事件流处理、IoT数据处理等
RabbitMQ 入门实战
特点：生态好、好学习、易于理解，时效性强，支持很多不同语言的客户端，扩展性、可用性都不错
学习性价比非常高的消息队列，适用于绝大多数的中小规模分布式系统

基本概念
AMQP协议：https://www.rabbitmq.com/tutorials/amqp-concepts

生产者：发消息到某个交换机
消费者：从某个队列中取消息
交换机：负责把消息转发到对应的队列
队列：存储消息
路由：转发，怎么把消息从一个地方转到另一个地方（比如从生产者转发到某个队列）

安装：网上教程搜索可知
官方文档：https://www.rabbitmq.com
快速开始：https://www.rabbitmq.com/#getstarted

引入消息队列 JavaClient 
<!-- https://mvnrepository.com/artifact/com.rabbitmq/amqp-client -->
<dependency>
  <groupId>com.rabbitmq</groupId>
  <artifactId>amqp-client</artifactId>
  <version>5.23.0</version>
</dependency>

一对一消费：

生产者代码：
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;

public class Send {

    private final static String QUEUE_NAME = "hello";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            String message = "Hello World!";
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent '" + message + "'");
        }
    }
}

Channel 频道：理解为操作消息队列的client（比如jdbcClient、redisClient），提供了和消息队列server建立通信的传输方法（为了复用连接，提高传输效率）。程序通过channel操作rabbitmq(收发消息）
创建消息队列
参数：
queueName：消息队列名称（注意：同名称的消息队列，智能用同样的参数创建一次）
durabale：消息队列重启后，消息是否丢失
exclusive：是否只允许当前这个创建消息队列的连接操作消息队列
autoDelete：没有人用队列后，是否要删除队列

消费者代码:
public class SingleConsumer {

    private final static String QUEUE_NAME = "hello";

    public static void main(String[] argv) throws Exception {
        // 创建连接
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        // 创建队列
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
        // 定义了如何处理消息
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");
        };
        // 消费消息，会持续阻塞
        channel.basicConsume(QUEUE_NAME, true, deliverCallback, consumerTag -> { });
    }
}

启动后，消息被消费了

多消费者

官方教程：https://www.rabbitmq.com/tutorials/tutorial-two-java
场景：多个机器同时去接受并处理任务（尤其是每个机器的处理能力有限）
一个生产者给一个队列发消息，多个消费者从这个队列中取消息。
1. 队列持久化
durable参数设置为true，服务器重启后队列不丢失
channel.queueDeclare(TASK_QUEUE_NAME, true, false, false, null);
2. 消息持久化
指定MessageProperties.PERSISTENT_TEXT_PLAIN参数
channel.basicPublish("", TASK_QUEUE_NAME,
        MessageProperties.PERSISTENT_TEXT_PLAIN,
        message.getBytes("UTF-8"));

生产者代码：
使用Scanner接受用户输入，便于发送多条消息

public class MultiProducer {

    private static final String TASK_QUEUE_NAME = "multi_queue";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.queueDeclare(TASK_QUEUE_NAME, true, false, false, null);

            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNext()) {
                String message = scanner.nextLine();
                channel.basicPublish("", TASK_QUEUE_NAME,
                                     MessageProperties.PERSISTENT_TEXT_PLAIN,
                                     message.getBytes("UTF-8"));
                System.out.println(" [x] Sent '" + message + "'");
            }
        }
    }

}
控制单个消费者的处理任务挤压数：
每个消费者最多同时处理1个任务
channel.basicQos(1);
重点：消息确认机制
为了保证消息成功被消费，rabbitmq 提供了消息确认机制，当消费者接受到消息后，比如要给一个反馈：
● ack：消费成功
● nack：消费失败
● reject：拒绝
如果告诉rabbitmq 服务器消费成功，服务器才会放心移除消息
支持配置autoack，会自动执行ack命令，接收到消息就立刻成功(但是建议autoack改为false，根据实际情况，手动确认）
channel.basicConsume(TASK_QUEUE_NAME, false, deliverCallback, consumerTag -> {
});
指定确认某条消息：
第二个参数multiple 批量确认：值是否要一次性确认所有历史消息知道当前这条
channel.basicAck(delivery.getEnvelope().getDeliveryTag(),false);
指定拒绝某条消息：
第三个参数表示是否重新入队，可用于重试
channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
消费者代码：
public class MultiConsumer {

    private static final String TASK_QUEUE_NAME = "multi_queue";

    public static void main(String[] argv) throws Exception {
        // 建立连接
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        final Connection connection = factory.newConnection();
        for (int i = 0; i < 2; i++) {
            final Channel channel = connection.createChannel();

            channel.queueDeclare(TASK_QUEUE_NAME, true, false, false, null);
            System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

            channel.basicQos(1);

            // 定义了如何处理消息
            int finalI = i;
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");

                try {
                    // 处理工作
                    System.out.println(" [x] Received '" + "编号:" + finalI + ":" + message + "'");
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    // 停 20 秒，模拟机器处理能力有限
                    Thread.sleep(20000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                } finally {
                    System.out.println(" [x] Done");
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                }
            };
            // 开启消费监听
            channel.basicConsume(TASK_QUEUE_NAME, false, deliverCallback, consumerTag -> {
            });
        }
    }
}

2个技巧：
1. 使用Scanner 接受用户输入，便于快速发送多条消息
2. 使用for 循环创建多个消费者，便于快速验证队列模型工作机制
交换机
场景：一个生产者给多个队列发消息，一个生产者对多个队列
交换机作用：类似网络路由器，提供转发功能。怎么把消息转发到不同的队列中。
解决的问题：怎么把消息转发到不同的队列中，让消费者从不同的队列消费
绑定：交换机和队列关联起来，也可以叫路由，算是一个算法或者转发策略


绑定代码：
 channel.queueBind(queueName, EXCHANGE_NAME, "绑定规则");
交换机有多种类别：fanout，direct，topic，headers
fanout
扇出、广播
特点：消息会被转发到所有绑定到该交换机的队列
场景：很适用于发布订阅的场景。比如写日志，可以多个系统间共享

生产者代码：
public class FanoutProducer {

  private static final String EXCHANGE_NAME = "fanout-exchange";

  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");
    try (Connection connection = factory.newConnection();
         Channel channel = connection.createChannel()) {
        // 创建交换机
        channel.exchangeDeclare(EXCHANGE_NAME, "fanout");
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNext()) {
            String message = scanner.nextLine();
            channel.basicPublish(EXCHANGE_NAME, "", null, message.getBytes("UTF-8"));
            System.out.println(" [x] Sent '" + message + "'");
        }
    }
  }
}

消费者代码：
注意：
1. 消费者和生产者要绑定同一个交换机
2. 要先有队列，才能绑定
public class FanoutConsumer {

  private static final String EXCHANGE_NAME = "fanout-exchange";

  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");
    Connection connection = factory.newConnection();
    Channel channel1 = connection.createChannel();
    Channel channel2 = connection.createChannel();
    // 声明交换机
    channel1.exchangeDeclare(EXCHANGE_NAME, "fanout");
    // 创建队列，随机分配一个队列名称
    String queueName = "xiaowang_queue";
    channel1.queueDeclare(queueName, true, false, false, null);
    channel1.queueBind(queueName, EXCHANGE_NAME, "");

    String queueName2 = "xiaoli_queue";
    channel2.queueDeclare(queueName2, true, false, false, null);
    channel2.queueBind(queueName2, EXCHANGE_NAME, "");
    channel2.queueBind(queueName2, EXCHANGE_NAME, "");

    System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

    DeliverCallback deliverCallback1 = (consumerTag, delivery) -> {
        String message = new String(delivery.getBody(), "UTF-8");
        System.out.println(" [小王] Received '" + message + "'");
    };

    DeliverCallback deliverCallback2 = (consumerTag, delivery) -> {
      String message = new String(delivery.getBody(), "UTF-8");
      System.out.println(" [小李] Received '" + message + "'");
    };
    channel1.basicConsume(queueName, true, deliverCallback1, consumerTag -> { });
    channel2.basicConsume(queueName2, true, deliverCallback2, consumerTag -> { });
  }
}

效果：所有消费者都能收到消息
Direct 交换机
绑定：可以让交换机和队列进行关联，可以指定让交换机吧什么样的消息发送给哪个队列
routingKey：路由键，控制消息发送给哪个队列（类似IP地址）
特点：特定的消息只交给特定的系统（程序）来处理
绑定关系：完全匹配字符串

可以绑定同样的路由键
比如发送日志的场景，希望用独立的程序来处理不同级别的日志

生产者代码：
public class DirectProducer {

  private static final String EXCHANGE_NAME = "direct-exchange";

  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");
    try (Connection connection = factory.newConnection();
         Channel channel = connection.createChannel()) {
        channel.exchangeDeclare(EXCHANGE_NAME, "direct");

        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNext()) {
            String userInput = scanner.nextLine();
            String[] strings = userInput.split(" ");
            if (strings.length < 1) {
                continue;
            }
            String message = strings[0];
            String routingKey = strings[1];

            channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes("UTF-8"));
            System.out.println(" [x] Sent '" + message + " with routing:" + routingKey + "'");
        }

    }
  }
  //..
}

消费者代码：
public class DirectConsumer {

    private static final String EXCHANGE_NAME = "direct-exchange";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.exchangeDeclare(EXCHANGE_NAME, "direct");

        // 创建队列，随机分配一个队列名称
        String queueName = "xiaoyu_queue";
        channel.queueDeclare(queueName, true, false, false, null);
        channel.queueBind(queueName, EXCHANGE_NAME, "xiaoyu");

        // 创建队列，随机分配一个队列名称
        String queueName2 = "xiaopi_queue";
        channel.queueDeclare(queueName2, true, false, false, null);
        channel.queueBind(queueName2, EXCHANGE_NAME, "xiaopi");

        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        DeliverCallback xiaoyuDeliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [xiaoyu] Received '" +
                    delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
        };

        DeliverCallback xiaopiDeliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [xiaopi] Received '" +
                    delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
        };

        channel.basicConsume(queueName, true, xiaoyuDeliverCallback, consumerTag -> {
        });
        channel.basicConsume(queueName2, true, xiaopiDeliverCallback, consumerTag -> {
        });
    }
}

Topic交换机
特点：消息会根据一个模糊的路由键转发到指定的队列
场景：特定的一类消息可以交给特定的一类系统来处理
绑定关系：可以模糊匹配多个绑定
● * .匹配一个单词
● 匹配0个或者多个单词
注意这里的匹配和MySQL的like的%不一样，智能按照单词来匹配，每个' . '分割单词
应用场景：老板要下发任务，让多个组来处理同一个任务，而不是所有组，需要设计一个匹配逻辑来匹配对应的组
生产者代码：
public class TopicProducer {

  private static final String EXCHANGE_NAME = "topic-exchange";

  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");
    try (Connection connection = factory.newConnection();
         Channel channel = connection.createChannel()) {

        channel.exchangeDeclare(EXCHANGE_NAME, "topic");

        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNext()) {
            String userInput = scanner.nextLine();
            String[] strings = userInput.split(" ");
            if (strings.length < 1) {
                continue;
            }
            String message = strings[0];
            String routingKey = strings[1];

            channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes("UTF-8"));
            System.out.println(" [x] Sent '" + message + " with routing:" + routingKey + "'");
        }
    }
  }
  //..
}

消费者代码：
public class TopicConsumer {

  private static final String EXCHANGE_NAME = "topic-exchange";

  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");
    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.exchangeDeclare(EXCHANGE_NAME, "topic");

      // 创建队列
      String queueName = "frontend_queue";
      channel.queueDeclare(queueName, true, false, false, null);
      channel.queueBind(queueName, EXCHANGE_NAME, "#.前端.#");

      // 创建队列
      String queueName2 = "backend_queue";
      channel.queueDeclare(queueName2, true, false, false, null);
      channel.queueBind(queueName2, EXCHANGE_NAME, "#.后端.#");

      // 创建队列
      String queueName3 = "product_queue";
      channel.queueDeclare(queueName3, true, false, false, null);
      channel.queueBind(queueName3, EXCHANGE_NAME, "#.产品.#");

      System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

      DeliverCallback xiaoaDeliverCallback = (consumerTag, delivery) -> {
          String message = new String(delivery.getBody(), "UTF-8");
          System.out.println(" [xiaoa] Received '" +
                  delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
      };

      DeliverCallback xiaobDeliverCallback = (consumerTag, delivery) -> {
          String message = new String(delivery.getBody(), "UTF-8");
          System.out.println(" [xiaob] Received '" +
                  delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
      };

      DeliverCallback xiaocDeliverCallback = (consumerTag, delivery) -> {
          String message = new String(delivery.getBody(), "UTF-8");
          System.out.println(" [xiaoc] Received '" +
                  delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
      };

      channel.basicConsume(queueName, true, xiaoaDeliverCallback, consumerTag -> {
      });
      channel.basicConsume(queueName2, true, xiaobDeliverCallback, consumerTag -> {
      });
      channel.basicConsume(queueName3, true, xiaocDeliverCallback, consumerTag -> {
      });
  }
}

消息过期机制
可以给每个消息指定一个有效期，一段时间没有被消费者处理，就过期了
场景：消费者（库存系统）挂了，一个订单15分钟还没被库存系统处理，这个订单已经失效了，在恢复，也不需要扣减库存了
适用场景：清理过期数据、模拟延迟队列的实现（不开会员就慢速）、专门让某个程序处理过期请求
1） 给队列中的所有消息指定过期时间
// 创建队列，指定消息过期参数
Map<String, Object> args = new HashMap<String, Object>();
args.put("x-message-ttl", 5000);
// args 指定参数
channel.queueDeclare(QUEUE_NAME, false, false, false, args);
注意：如果在过期时间内，没有消费者取出消息，消息才会过期
如果消息已经接收到，但是没确认，不会过期
个人理解：这里只要是给快递站做了提醒，说我什么时候来取，快递站就会保留快递，不会过期；如果长时间未给快递站反馈，快递站就会退货处理。
消费者代码：
public class TtlConsumer {

    private final static String QUEUE_NAME = "ttl_queue";

    public static void main(String[] argv) throws Exception {
        // 创建连接
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // 创建队列，指定消息过期参数
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-message-ttl", 5000);
        // args 指定参数
        channel.queueDeclare(QUEUE_NAME, false, false, false, args);

        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
        // 定义了如何处理消息
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");
        };
        // 消费消息，会持续阻塞
        channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> { });
    }
}

生产者代码：
public class TtlProducer {

    private final static String QUEUE_NAME = "ttl_queue";

    public static void main(String[] argv) throws Exception {
        // 创建连接工厂
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
//        factory.setUsername();
//        factory.setPassword();
//        factory.setPort();

        // 建立连接、创建频道
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            // 发送消息
            String message = "Hello World!";
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent '" + message + "'");
        }
    }
}

2）给某条消息指定过期时间
// 给消息指定过期时间
AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
        .expiration("1000")
        .build();
channel.basicPublish("my-exchange", "routing-key", properties, message.getBytes(StandardCharsets.UTF_8));

示例代码：
public class TtlProducer {

    private final static String QUEUE_NAME = "ttl_queue";

    public static void main(String[] argv) throws Exception {
        // 创建连接工厂
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
//        factory.setUsername();
//        factory.setPassword();
//        factory.setPort();

        // 建立连接、创建频道
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            // 发送消息
            String message = "Hello World!";

            // 给消息指定过期时间
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .expiration("1000")
                    .build();
            channel.basicPublish("my-exchange", "routing-key", properties, message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent '" + message + "'");
        }
    }
}

消息确认机制：
为了保证消息成功被消费，提供了消息确认机制，当消费者接收到消息后，比如要给一个反馈：
● ack：消费成功
● nack：消费失败
● reject：拒绝
如果告诉rabbitmq 服务器消费成功，服务器才会放心移除消息
支持配置autoack ，会自动执行ack命令，接收到消息立刻就成功了
个人建议：这里一般自己手动确认，根据想什么时候确认就什么时候确认
 channel.basicConsume(TASK_QUEUE_NAME, false, deliverCallback, consumerTag -> {
            });

指定确认某条消息
channel.basicAck(delivery.getEnvelope().getDeliveryTag(), "这个参数指是否要一次性确认所有历史消息到当前这条");


死信队列
官方文档：https://www.rabbitmq.com/docs/dlx
为了保证消息的可靠性，比如每条消息都成功消费，需要提供一个容错机制。
死信：过期的消息、拒收的消息、消息队列满了、处理失败的消息的统称
死信队列：专门处理死信的队列（注意：他就是一个普通队列，只不过专门处理死信的，可以理解为这个队列名称叫做死信队列）
死信交换机：专门给死信队列转发消息的交换机（注意：他就是要给普通交换机，只不过专门给死信队列发消息而已，理解为交换机名称叫做死信交换机）。存在路由绑定
死信可以通过死信交换机绑定到死信队列

实现
1） 创建死信交换机和死信队列，并且绑定关系
2）给失败后需要容错的队列绑定死信交换机
代码：
// 指定死信队列参数
Map<String, Object> args = new HashMap<>();
// 要绑定到哪个交换机
args.put("x-dead-letter-exchange", DEAD_EXCHANGE_NAME);
// 指定死信要转发到哪个死信队列
args.put("x-dead-letter-routing-key", "waibao");

// 创建队列，随机分配一个队列名称
String queueName = "xiaodog_queue";
channel.queueDeclare(queueName, true, false, false, args);
channel.queueBind(queueName, EXCHANGE_NAME, "xiaodog");

3）给要容错的队列指定死信之后的转发规则，死信应该转发到哪个死信队列
// 指定死信要转发到哪个死信队列
args.put("x-dead-letter-routing-key", "waibao");

4)可以通过程序来读取死信队列中的消息，从而处理
// 创建队列，随机分配一个队列名称
String queueName = "laoban_dlx_queue";
channel.queueDeclare(queueName, true, false, false, null);
channel.queueBind(queueName, DEAD_EXCHANGE_NAME, "laoban");

String queueName2 = "waibao_dlx_queue";
channel.queueDeclare(queueName2, true, false, false, null);
channel.queueBind(queueName2, DEAD_EXCHANGE_NAME, "waibao");

DeliverCallback laobanDeliverCallback = (consumerTag, delivery) -> {
    String message = new String(delivery.getBody(), "UTF-8");
    // 拒绝消息
    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
    System.out.println(" [laoban] Received '" +
            delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
};

DeliverCallback waibaoDeliverCallback = (consumerTag, delivery) -> {
    String message = new String(delivery.getBody(), "UTF-8");
    // 拒绝消息
    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
    System.out.println(" [waibao] Received '" +
            delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
};

channel.basicConsume(queueName, false, laobanDeliverCallback, consumerTag -> {
});
channel.basicConsume(queueName2, false, waibaoDeliverCallback, consumerTag -> {
});


生产者代码：
public class DlxDirectProducer {

    private static final String DEAD_EXCHANGE_NAME = "dlx-direct-exchange";
    private static final String WORK_EXCHANGE_NAME = "direct2-exchange";


    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            // 声明死信交换机
            channel.exchangeDeclare(DEAD_EXCHANGE_NAME, "direct");

            // 创建队列，随机分配一个队列名称
            String queueName = "laoban_dlx_queue";
            channel.queueDeclare(queueName, true, false, false, null);
            channel.queueBind(queueName, DEAD_EXCHANGE_NAME, "laoban");

            String queueName2 = "waibao_dlx_queue";
            channel.queueDeclare(queueName2, true, false, false, null);
            channel.queueBind(queueName2, DEAD_EXCHANGE_NAME, "waibao");

            DeliverCallback laobanDeliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");
                // 拒绝消息
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                System.out.println(" [laoban] Received '" +
                        delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
            };

            DeliverCallback waibaoDeliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");
                // 拒绝消息
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                System.out.println(" [waibao] Received '" +
                        delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
            };

            channel.basicConsume(queueName, false, laobanDeliverCallback, consumerTag -> {
            });
            channel.basicConsume(queueName2, false, waibaoDeliverCallback, consumerTag -> {
            });


            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNext()) {
                String userInput = scanner.nextLine();
                String[] strings = userInput.split(" ");
                if (strings.length < 1) {
                    continue;
                }
                String message = strings[0];
                String routingKey = strings[1];

                channel.basicPublish(WORK_EXCHANGE_NAME, routingKey, null, message.getBytes("UTF-8"));
                System.out.println(" [x] Sent '" + message + " with routing:" + routingKey + "'");
            }

        }
    }
    //..
}

消费者完整代码：
public class DlxDirectConsumer {

    private static final String DEAD_EXCHANGE_NAME = "dlx-direct-exchange";

    private static final String WORK_EXCHANGE_NAME = "direct2-exchange";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.exchangeDeclare(WORK_EXCHANGE_NAME, "direct");

        // 指定死信队列参数
        Map<String, Object> args = new HashMap<>();
        // 要绑定到哪个交换机
        args.put("x-dead-letter-exchange", DEAD_EXCHANGE_NAME);
        // 指定死信要转发到哪个死信队列
        args.put("x-dead-letter-routing-key", "waibao");

        // 创建队列，随机分配一个队列名称
        String queueName = "xiaodog_queue";
        channel.queueDeclare(queueName, true, false, false, args);
        channel.queueBind(queueName, WORK_EXCHANGE_NAME, "xiaodog");

        Map<String, Object> args2 = new HashMap<>();
        args2.put("x-dead-letter-exchange", DEAD_EXCHANGE_NAME);
        args2.put("x-dead-letter-routing-key", "laoban");

        // 创建队列，随机分配一个队列名称
        String queueName2 = "xiaocat_queue";
        channel.queueDeclare(queueName2, true, false, false, args2);
        channel.queueBind(queueName2, WORK_EXCHANGE_NAME, "xiaocat");

        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        DeliverCallback xiaoyuDeliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            // 拒绝消息
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            System.out.println(" [xiaodog] Received '" +
                    delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
        };

        DeliverCallback xiaopiDeliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            // 拒绝消息
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            System.out.println(" [xiaocat] Received '" +
                    delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
        };

        channel.basicConsume(queueName, false, xiaoyuDeliverCallback, consumerTag -> {
        });
        channel.basicConsume(queueName2, false, xiaopiDeliverCallback, consumerTag -> {
        });
    }
}

重点知识：
1. 消息队列的概念、模型、应用场景
2. 交换机的类别、路由绑定的关系
3. 消息可靠性
  a. 消息确认机制（ack、nack、reject)
  b. 消息持久化（设置个参数durable)
  c. 消息过期机制
  d. 死信队列
4. 延迟队列（类似死信队列）
5. 顺序消费、消费幂等性（TODO)
6. 可扩展性
  a. 集群
  b. 故障恢复机制
  c. 镜像
7. 运维监控告警

RabbitMQ项目实战
1）使用官方的客户端
优点：兼容性好，支持多种语言，比较灵活
缺点：太灵活，要自己处理一些事情，要自己维护管理链接
2）使用封装好的客户端，比如spring boot rabbitmq starter
优点：简单易用，直接配置直接用，方便
缺点，封装后不够灵活，被框架限制
基础实战
1） 引入依赖
<!-- https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-amqp -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
  <version>2.7.2</version>
</dependency>


2）yml中配置
spring:
rabbitmq:
host: localhost
port: 5672
password: guest
username: guest

3）创建交换机和队列
/**
 * 用于创建测试程序用到的交换机和队列（只用在程序启动前执行一次）
 */
public class MqInitMain {

    public static void main(String[] args) {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();
            String EXCHANGE_NAME = "code_exchange";
            channel.exchangeDeclare(EXCHANGE_NAME, "direct");

            // 创建队列，随机分配一个队列名称
            String queueName = "code_queue";
            channel.queueDeclare(queueName, true, false, false, null);
            channel.queueBind(queueName, EXCHANGE_NAME, "my_routingKey");
        } catch (Exception e) {

        }

    }
}

4）生产者代码
@Component
public class MyMessageProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    public void sendMessage(String exchange, String routingKey, String message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }

}

5）消费者代码
@Component
@Slf4j
public class MyMessageConsumer {

    // 指定程序监听的消息队列和确认机制
    @SneakyThrows
    @RabbitListener(queues = {"code_queue"}, ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("receiveMessage message = {}", message);
        channel.basicAck(deliveryTag, false);
    }

}

6）单元测试
@SpringBootTest
class MyMessageProducerTest {

    @Resource
    private MyMessageProducer myMessageProducer;

    @Test
    void sendMessage() {
        myMessageProducer.sendMessage("code_exchange", "my_routingKey", "你好呀");
    }
}

BI项目改造
任务提交到线程池，如果程序中断了任务就没了
改造后：
1. 任务改成向队列发消息
2. 写一个专门的接受任务的程序，处理任务
3. 如果程序中断了，消息未被确认，还会重发
4. 消息集中发到消息队列中，可以部署多个后端，从同一个地方取出任务，实现的分布式负载均衡
实现步骤
1）创建交换机和队列
2）将线程池中的执行代码转移到消费者类中
3）根据消费者的需求来确认消息的格式（chartId)
4）将提交线程池改造为发送消息到队列
验证
程序中断，没有ack、也没有nack（服务中断，没有任何响应）、那么这条消息会被重新放到消息队列中，从而实现了每个任务都会执行
