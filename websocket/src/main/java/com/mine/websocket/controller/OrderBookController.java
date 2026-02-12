package com.mine.websocket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OrderBookController {

    @GetMapping("/orderbook")
    public String orderbook() {
        return "orderbook";
    }
}
