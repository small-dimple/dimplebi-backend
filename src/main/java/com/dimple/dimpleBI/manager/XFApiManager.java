package com.dimple.dimpleBI.manager;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import com.dimple.dimpleBI.model.dto.chart.Message;
import com.dimple.dimpleBI.model.dto.chart.XFApiRequestBody;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class XFApiManager  {

    private static final String API_URL = "https://spark-api-open.xf-yun.com/v1/chat/completions";
    private static final String API_KEY = "iHMrhfzxhzZPoAWhGdof:cjbmNWeDicimuRgQaLbP";  // 使用实际的API密钥
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
