package com.atguigu.gmall.index.controller;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.service.IndexService;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class IndexController {

    @Autowired
    private IndexService indexService;

    @GetMapping
    public String toIndex(Model model){

        List<CategoryEntity> categoryEntityList = this.indexService.queryLvl1Categories();
        model.addAttribute("categories", categoryEntityList);

        // TODO: 加载广告

        return "index";
    }

    @GetMapping("index/cates/{pid}")
    @ResponseBody
    public ResponseVo<List<CategoryEntity>> queryLvl2WithSubByPid(@PathVariable("pid")Long pid){
        List<CategoryEntity> categoryEntities = this.indexService.queryLvl2WithSubByPid(pid);
        return ResponseVo.ok(categoryEntities);
    }

    @GetMapping("index/test/lock")
    @ResponseBody
    public ResponseVo testLock(){
        this.indexService.testLock();
        return ResponseVo.ok();
    }

    @GetMapping("index/test/read")
    @ResponseBody
    public ResponseVo testRead(){
        this.indexService.testRead();
        return ResponseVo.ok();
    }

    @GetMapping("index/test/write")
    @ResponseBody
    public ResponseVo testWrite(){
        this.indexService.testWrite();
        return ResponseVo.ok();
    }

    @GetMapping("index/test/semaphore")
    @ResponseBody
    public ResponseVo testsSemaphore(){
        this.indexService.testsSemaphore();
        return ResponseVo.ok();
    }

    @GetMapping("index/test/latch")
    @ResponseBody
    public ResponseVo testsLatch(){
        this.indexService.testsLatch();
        return ResponseVo.ok("班长锁门。。。。。");
    }

    @GetMapping("index/test/countDown")
    @ResponseBody
    public ResponseVo testsCountDown(){
        this.indexService.testsCountDown();
        return ResponseVo.ok("出来了一位同学");
    }
}
