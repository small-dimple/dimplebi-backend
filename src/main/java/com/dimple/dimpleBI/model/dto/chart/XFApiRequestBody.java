package com.dimple.dimpleBI.model.dto.chart;

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