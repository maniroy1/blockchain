package com.moglix.reports.controllers;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.moglix.reports.fabric.sdkintegration.End2endIT;
import com.moglix.reports.fabric.sdkintegration.Operations;
import com.moglix.reports.model.Agreement;


@RestController
public class TestController {
    
    @RequestMapping("/ping")
    public String ping() {
        return "Welcome to Moglix Report Card Module";
    }
    
	@RequestMapping("/chaincode/create")
    List<Object> create(@RequestHeader(value = "clientId", required = false) String clientId,
                             @RequestBody Agreement jsonData) throws Exception {
		
		End2endIT e = new End2endIT();
		List<Object> obj = new ArrayList<>();
		
		List<String> res = e.tranaction(new Gson().toJson(jsonData));
		
		for(String str : res) {
			JsonObject jsonObject = new JsonObject();
	        Gson gson = new Gson();
	        @SuppressWarnings("deprecation")
			net.minidev.json.parser.JSONParser jsonParser = new net.minidev.json.parser.JSONParser();
	        Object object = jsonParser.parse(str);
	        obj.add(object);
		}
		return obj;
	}
	
	@RequestMapping("/chaincode/trasact")
    List<Object> transact(@RequestHeader(value = "clientId", required = false) String clientId,
                             @RequestBody Agreement jsonData) throws Exception {
		
		Operations o = new Operations();
		List<Object> obj = new ArrayList<>();
		
		System.out.println("transactApiCall : " + jsonData.toString());
		
		
		List<String> res = o.tranaction(0, new Gson().toJson(jsonData));
		
		for(String str : res) {
			JsonObject jsonObject = new JsonObject();
	        Gson gson = new Gson();
	        @SuppressWarnings("deprecation")
			net.minidev.json.parser.JSONParser jsonParser = new net.minidev.json.parser.JSONParser();
	        Object object = jsonParser.parse(str);
	        obj.add(object);
		}
		return obj;
	}
	
	@RequestMapping("/chaincode/query")
    List<Object> query(@RequestHeader(value = "clientId", required = false) String clientId) throws Exception {
		Operations o = new Operations();
		List<Object> obj = new ArrayList<>();
		
		List<String> res = o.tranaction(1, "");
		
		for(String str : res) {
			JsonObject jsonObject = new JsonObject();
	        Gson gson = new Gson();
	        @SuppressWarnings("deprecation")
			net.minidev.json.parser.JSONParser jsonParser = new net.minidev.json.parser.JSONParser();
	        
	        Object object = jsonParser.parse(str);
	        obj.add(object);
		}
		return obj;
	}
	
	@RequestMapping("/chaincode/history")
    List<Object> history(@RequestHeader(value = "clientId", required = false) String clientId) throws Exception {
		
		Operations o = new Operations();
		List<Object> obj = new ArrayList<>();
		
		List<String> res = o.tranaction(2, "");
		for(String str : res) {
			JsonObject jsonObject = new JsonObject();
	        Gson gson = new Gson();
	        @SuppressWarnings("deprecation")
			net.minidev.json.parser.JSONParser jsonParser = new net.minidev.json.parser.JSONParser();
	        System.out.println("historygetcall : " + str );
	        Object object = jsonParser.parse(str);
	        obj.add(object);
		}
		return obj;
	}

}
