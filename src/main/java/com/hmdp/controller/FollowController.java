package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService iFollowService;

    @PutMapping("/{followUserId}/{is}")
    public Result follow(@PathVariable("followUserId")Long followUserId,@PathVariable("is") Boolean is){
       return iFollowService.follow(followUserId,is);
    }

    @GetMapping("/or/not/{followUserId}")
    public Result IsFollow(@PathVariable("followUserId")Long followUserId){
        return iFollowService.queryByUserId(followUserId);
    }

    @GetMapping("/commons/{id}")
    public Result commonsUsers(@PathVariable("id") Long userId){
        return iFollowService.commonsUsers(userId);
    }

}
