
package com.controller;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.text.SimpleDateFormat;
import com.alibaba.fastjson.JSONObject;
import java.util.*;
import org.springframework.beans.BeanUtils;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.context.ContextLoader;
import javax.servlet.ServletContext;
import com.service.TokenService;
import com.utils.*;
import java.lang.reflect.InvocationTargetException;

import com.service.DictionaryService;
import org.apache.commons.lang3.StringUtils;
import com.annotation.IgnoreAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.entity.*;
import com.entity.view.*;
import com.service.*;
import com.utils.PageUtils;
import com.utils.R;
import com.alibaba.fastjson.*;

/**
 * 知识
 * 后端接口
 * @author
 * @email
*/
@RestController
@Controller
@RequestMapping("/zhishi")
public class ZhishiController {
    private static final Logger logger = LoggerFactory.getLogger(ZhishiController.class);

    @Autowired
    private ZhishiService zhishiService;


    @Autowired
    private TokenService tokenService;
    @Autowired
    private DictionaryService dictionaryService;

    //级联表service

    @Autowired
    private YonghuService yonghuService;


    /**
    * 后端列表
    */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params, HttpServletRequest request){
        logger.debug("page方法:,,Controller:{},,params:{}",this.getClass().getName(),JSONObject.toJSONString(params));
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(false)
            return R.error(511,"永不会进入");
        else if("用户".equals(role))
            params.put("yonghuId",request.getSession().getAttribute("userId"));
        if(params.get("orderBy")==null || params.get("orderBy")==""){
            params.put("orderBy","id");
        }
        PageUtils page = zhishiService.queryPage(params);

        //字典表数据转换
        List<ZhishiView> list =(List<ZhishiView>)page.getList();
        for(ZhishiView c:list){
            //修改对应字典表字段
            dictionaryService.dictionaryConvert(c, request);
        }
        return R.ok().put("data", page);
    }

    /**
    * 后端详情
    */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id, HttpServletRequest request){
        logger.debug("info方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
        ZhishiEntity zhishi = zhishiService.selectById(id);
        if(zhishi !=null){
            //entity转view
            ZhishiView view = new ZhishiView();
            BeanUtils.copyProperties( zhishi , view );//把实体数据重构到view中

            //修改对应字典表字段
            dictionaryService.dictionaryConvert(view, request);
            return R.ok().put("data", view);
        }else {
            return R.error(511,"查不到数据");
        }

    }

    /**
    * 后端保存
    */
    @RequestMapping("/save")
    public R save(@RequestBody ZhishiEntity zhishi, HttpServletRequest request){
        logger.debug("save方法:,,Controller:{},,zhishi:{}",this.getClass().getName(),zhishi.toString());

        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(false)
            return R.error(511,"永远不会进入");

        Wrapper<ZhishiEntity> queryWrapper = new EntityWrapper<ZhishiEntity>()
            .eq("zhishi_name", zhishi.getZhishiName())
            .eq("zhishi_types", zhishi.getZhishiTypes())
            ;

        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        ZhishiEntity zhishiEntity = zhishiService.selectOne(queryWrapper);
        if(zhishiEntity==null){
            zhishi.setInsertTime(new Date());
            zhishi.setCreateTime(new Date());
            zhishiService.insert(zhishi);
            return R.ok();
        }else {
            return R.error(511,"表中有相同数据");
        }
    }

    /**
    * 后端修改
    */
    @RequestMapping("/update")
    public R update(@RequestBody ZhishiEntity zhishi, HttpServletRequest request){
        logger.debug("update方法:,,Controller:{},,zhishi:{}",this.getClass().getName(),zhishi.toString());

        String role = String.valueOf(request.getSession().getAttribute("role"));
//        if(false)
//            return R.error(511,"永远不会进入");
        //根据字段查询是否有相同数据
        Wrapper<ZhishiEntity> queryWrapper = new EntityWrapper<ZhishiEntity>()
            .notIn("id",zhishi.getId())
            .andNew()
            .eq("zhishi_name", zhishi.getZhishiName())
            .eq("zhishi_types", zhishi.getZhishiTypes())
            ;

        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        ZhishiEntity zhishiEntity = zhishiService.selectOne(queryWrapper);
        if("".equals(zhishi.getZhishiFile()) || "null".equals(zhishi.getZhishiFile())){
                zhishi.setZhishiFile(null);
        }
        if(zhishiEntity==null){
            zhishiService.updateById(zhishi);//根据id更新
            return R.ok();
        }else {
            return R.error(511,"表中有相同数据");
        }
    }

    /**
    * 删除
    */
    @RequestMapping("/delete")
    public R delete(@RequestBody Integer[] ids){
        logger.debug("delete:,,Controller:{},,ids:{}",this.getClass().getName(),ids.toString());
        zhishiService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }


    /**
     * 批量上传
     */
    @RequestMapping("/batchInsert")
    public R save( String fileName){
        logger.debug("batchInsert方法:,,Controller:{},,fileName:{}",this.getClass().getName(),fileName);
        try {
            List<ZhishiEntity> zhishiList = new ArrayList<>();//上传的东西
            Map<String, List<String>> seachFields= new HashMap<>();//要查询的字段
            Date date = new Date();
            int lastIndexOf = fileName.lastIndexOf(".");
            if(lastIndexOf == -1){
                return R.error(511,"该文件没有后缀");
            }else{
                String suffix = fileName.substring(lastIndexOf);
                if(!".xls".equals(suffix)){
                    return R.error(511,"只支持后缀为xls的excel文件");
                }else{
                    URL resource = this.getClass().getClassLoader().getResource("static/upload/" + fileName);//获取文件路径
                    File file = new File(resource.getFile());
                    if(!file.exists()){
                        return R.error(511,"找不到上传文件，请联系管理员");
                    }else{
                        List<List<String>> dataList = PoiUtil.poiImport(file.getPath());//读取xls文件
                        dataList.remove(0);//删除第一行，因为第一行是提示
                        for(List<String> data:dataList){
                            //循环
                            ZhishiEntity zhishiEntity = new ZhishiEntity();
//                            zhishiEntity.setZhishiName(data.get(0));                    //知识名称 要改的
//                            zhishiEntity.setZhishiFile(data.get(0));                    //知识文件 要改的
//                            zhishiEntity.setZhishiTypes(Integer.valueOf(data.get(0)));   //知识类型 要改的
//                            zhishiEntity.setZhishiContent("");//照片
//                            zhishiEntity.setInsertTime(date);//时间
//                            zhishiEntity.setCreateTime(date);//时间
                            zhishiList.add(zhishiEntity);


                            //把要查询是否重复的字段放入map中
                        }

                        //查询是否重复
                        zhishiService.insertBatch(zhishiList);
                        return R.ok();
                    }
                }
            }
        }catch (Exception e){
            return R.error(511,"批量插入数据异常，请联系管理员");
        }
    }






}
