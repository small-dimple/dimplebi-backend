package com.dimple.dimpleBI.model.dto.chart;

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