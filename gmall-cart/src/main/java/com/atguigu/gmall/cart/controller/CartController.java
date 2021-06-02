package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Controller
public class CartController {

    @Autowired
    private CartService cartService;

    /**
     * 新增购物车：cart.gmall.com?skuId=111&count=3
     * @param cart
     * @return
     */
    @GetMapping
    public String addCart(Cart cart){

        this.cartService.addCart(cart);

        return "redirect:http://cart.gmall.com/addCart.html?skuId=" + cart.getSkuId() + "&count=" + cart.getCount();
    }

    @GetMapping("addCart.html")
    public String queryCart(@RequestParam("skuId")Long skuId,
                            @RequestParam("count")Integer count, Model model){
        Cart cart = this.cartService.queryCart(skuId);
        cart.setCount(new BigDecimal(count));
        model.addAttribute("cart", cart);
        return "addCart";
    }

    @GetMapping("cart.html")
    public String queryCarts(Model model){
        List<Cart> cartList = this.cartService.queryCarts();
        model.addAttribute("carts", cartList);
        return "cart";
    }

    @PostMapping("updateNum")
    @ResponseBody
    public ResponseVo updateNum(@RequestBody Cart cart){
        this.cartService.updateNum(cart);
        return ResponseVo.ok();
    }

    @PostMapping("deleteCart")
    @ResponseBody
    public ResponseVo deleteCart(@RequestParam("skuId")Long skuId){
        this.cartService.deleteCart(skuId);
        return ResponseVo.ok();
    }

    @GetMapping("test")
    @ResponseBody
    public String test(HttpServletRequest request) throws ExecutionException, InterruptedException {
        //System.out.println(this.loginInterceptor.getUserId());
        //System.out.println(request.getAttribute("userId"));
        //System.out.println(LoginInterceptor.getUserInfo());
        long now = System.currentTimeMillis();
        System.out.println("controller方法开始执行=================");
        this.cartService.executor1();
        this.cartService.executor2();
//        future1.addCallback(result -> {
//            System.out.println("executor1执行结果：" + result);
//        }, ex -> {
//            System.out.println("executor1执行异常：" + ex.getMessage());
//        });
//        future2.addCallback(result -> {
//            System.out.println("executor2执行结果：" + result);
//        }, ex -> {
//            System.out.println("executor2执行异常：" + ex.getMessage());
//        });
//        System.out.println(future1.get());
//        System.out.println(future2.get());
        System.out.println("controller方法结束执行=================" + (System.currentTimeMillis() - now));
        return "hello test;";
    }
}
