package com.simon.controller;

import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.Sheet;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.fastjson.JSON;
import com.simon.common.controller.BaseController;
import com.simon.common.domain.EasyUIDataGridResult;
import com.simon.common.domain.ResultCode;
import com.simon.common.domain.ResultMsg;
import com.simon.common.domain.UserEntity;
import com.simon.common.utils.BeanUtils;
import com.simon.dto.ChangePasswordDto;
import com.simon.model.OauthUser;
import com.simon.service.DictTypeService;
import com.simon.service.OauthUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 用户表
 *
 * @author SimonSun
 * @date 2019-01-22
 **/
@Slf4j
@Api(description = "用户表")
@Controller
@RequestMapping("/api/oauthUsers")
public class OauthUserController extends BaseController {

    @Autowired
    private OauthUserService oauthUserService;

    @Autowired
    private DictTypeService dictTypeService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @ApiIgnore
    @ApiOperation(value = "vue列表页面")
    @GetMapping("list")
    public String list(Model model) {
        model.addAttribute("sexTypeList", listToMap(dictTypeService.getTypeByGroupCode("sex_type")));
        model.addAttribute("loginTypeList", listToMap(dictTypeService.getTypeByGroupCode("login_type")));
        model.addAttribute("loginStatusList", listToMap(dictTypeService.getTypeByGroupCode("login_status")));
        model.addAttribute("enabledStatusList", listToMap(dictTypeService.getTypeByGroupCode("enabled_status")));
        return "vue/oauthUser/list";
    }

    @ApiIgnore
    @ApiOperation(value = "新增页面")
    @GetMapping("add")
    public String add() {
        return "vue/oauthUser/add";
    }

    @ApiIgnore
    @ApiOperation(value = "编辑页面")
    @GetMapping("edit")
    public String edit(@RequestParam Long id, Model model) {
        model.addAttribute("entity", entityToMap(oauthUserService.findById(id)));
        return "vue/oauthUser/edit";
    }

    @ApiIgnore
    @ApiOperation(value = "列表数据")
    @GetMapping("data")
    @ResponseBody
    public EasyUIDataGridResult<OauthUser> data(
            @ApiParam(value = "用户名") @RequestParam(required = false) String username,
            @ApiParam(value = "有效") @RequestParam(required = false) Boolean enabled,
            @ApiParam(value = "手机号") @RequestParam(required = false) String phone,
            @ApiParam(value = "邮箱") @RequestParam(required = false) String email,
            @ApiParam(value = "性别") @RequestParam(required = false) Boolean sex,
            @ApiParam(value = "页码", defaultValue = "1", required = true) @RequestParam Integer pageNo,
            @ApiParam(value = "每页条数", defaultValue = "10", required = true) @RequestParam Integer pageSize,
            @ApiParam(value = "排序") @RequestParam(required = false, defaultValue = "") String orderBy) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("username", username);
        params.put("enabled", enabled);
        params.put("phone", phone);
        params.put("email", email);
        params.put("sex", sex);
        var list = oauthUserService.getList(params, pageNo, pageSize, orderBy);
        //测试element table延时动画
        /*try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/
        return new EasyUIDataGridResult<>(list);
    }

    @PermitAll
    @GetMapping("demoData")
    @ResponseBody
    public List<OauthUser> getAll() {
        return oauthUserService.findAll();
    }

    @ApiOperation(value = "新增")
    @PostMapping("add")
    @ResponseBody
    public ResponseEntity<ResultMsg> add(@RequestBody OauthUser body, Authentication authentication) {
        if (oauthUserService.countByPhoneOrEmail(body.getPhone(), body.getEmail()) > 0) {
            return new ResponseEntity<>(ResultMsg.fail(ResultCode.FAIL_PHONE_OR_EMAIL_EXISTS), HttpStatus.CONFLICT);
        }

        UserEntity userEntity = getCurrentUser(authentication);
        body.setCreateDate(LocalDateTime.now());
        body.setCreateBy(userEntity.getId());
        oauthUserService.insertSelective(body);
        return new ResponseEntity<>(ResultMsg.success(), HttpStatus.CREATED);
    }

    @ApiOperation(value = "修改")
    @PatchMapping("edit")
    @ResponseBody
    public ResultMsg update(@RequestBody OauthUser body, Authentication authentication) {
        UserEntity userEntity = getCurrentUser(authentication);
        body.setUpdateDate(LocalDateTime.now());
        body.setUpdateBy(userEntity.getId());
        oauthUserService.updateByPrimaryKeySelective(body);
        return ResultMsg.success();
    }

    @ApiOperation(value = "删除")
    @DeleteMapping("/ids/{ids}")
    @ResponseBody
    public ResultMsg delete(@PathVariable String ids) {
        oauthUserService.deleteByIds(ids);
        return ResultMsg.success();
    }

    @ApiIgnore
    @PreAuthorize("isAuthenticated()")
    @ApiOperation(value = "个人中心")
    @GetMapping("/personalCenter")
    public String personalCenter(Model model, Authentication authentication) {
        UserEntity userEntity = getCurrentUser(authentication);
        if (null != userEntity) {
            log.info(JSON.toJSONString(userEntity));
            model.addAttribute("user", userEntity);
        }
        return "easyui/personal_center";
    }

    @ApiOperation(value = "更新个人信息")
    @PatchMapping("personalCenter/edit")
    @ResponseBody
    public ResultMsg updatePersonInfo(@RequestBody OauthUser oauthUser, Authentication authentication) {
        oauthUserService.updateByPrimaryKeySelective(oauthUser);

//        log.info("birth=" + new SimpleDateFormat("yyyy-MM-dd").format(oauthUser.getBirth()));
        log.info(JSON.toJSONString(oauthUser));

        Object principal = authentication.getPrincipal();
        UserEntity userEntity = null;
        if (principal instanceof UserEntity) {
            userEntity = (UserEntity) principal;
        }
        if (null != userEntity) {
            //更新session中的principal
            BeanUtils.copyPropertiesIgnoreNull(oauthUser, userEntity);
        }

        return ResultMsg.success();
    }

    @ApiIgnore
    @ApiOperation(value = "导出")
    @GetMapping("export")
    public void exportExcel(HttpServletRequest request, HttpServletResponse response) {
        OutputStream out = null;
        try {
            String filePath = "用户信息.xlsx";
            filePath = URLEncoder.encode(filePath, "UTF-8");
            out = response.getOutputStream();
            response.reset();
            if (request.getHeader("User-Agent").toLowerCase().indexOf("firefox") > -1) {
                //使用Content-Disposition: attachment; filename=FILENAME，在Firefox浏览器中下载文件，文件名中文乱码问题解决。
                response.setHeader("Content-disposition", "attachment; filename*=UTF-8''" + filePath);
            } else {
                response.setHeader("Content-disposition", "attachment; filename=" + filePath);
            }
            response.setContentType("application/x-download");
            ExcelWriter writer = new ExcelWriter(out, ExcelTypeEnum.XLSX);
            Sheet sheet1 = new Sheet(1, 0, OauthUser.class);
            writer.write(oauthUserService.findAll(), sheet1);
            writer.finish();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != out) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    @ApiIgnore
    @ApiOperation(value = "导入")
    @GetMapping("import")
    @ResponseBody
    public ResultMsg importExcel() {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream("logs/user.xlsx");
            //解析每行结果在listener中处理
            ExcelListener listener = new ExcelListener();
            ExcelReader excelReader = new ExcelReader(inputStream, null, listener);
            excelReader.read(new Sheet(1, 2, OauthUser.class));
            log.info(JSON.toJSONString(listener.getDataList()));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != inputStream) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return ResultMsg.success();
    }

    private class ExcelListener extends AnalysisEventListener {
        /**
         * 自定义用于暂时存储data
         * 可以通过实例获取该值
         */
        private List<Object> dataList = new ArrayList<>();

        @Override
        public void invoke(Object object, AnalysisContext analysisContext) {
            //数据存储到list，供批量处理，或后续自己业务逻辑处理。
            dataList.add(object);
            //根据自己业务做处理
            doSomething(object);
        }

        private void doSomething(Object object) {
            //1、入库调用接口
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext analysisContext) {
            //解析结束销毁不用的资源
            //dataList.clear();
        }

        public List<Object> getDataList() {
            return dataList;
        }

        public void setDataList(List<Object> dataList) {
            this.dataList = dataList;
        }
    }

    @ApiIgnore
    @PostMapping("/changePassword")
    @ResponseBody
    public ResponseEntity<ResultMsg> changePassword(@RequestBody ChangePasswordDto body, Authentication authentication) {
        UserEntity userEntity = getCurrentUser(authentication);
        if (!passwordEncoder.matches(body.getOldPassword(), userEntity.getPassword())) {
            return new ResponseEntity<>(ResultMsg.fail(ResultCode.FAIL_INCORRECT_PASSWORD), HttpStatus.NOT_FOUND);
        }
        oauthUserService.updatePasswordByUserId(userEntity.getId(), passwordEncoder.encode(body.getNewPassword()));

        return new ResponseEntity<>(ResultMsg.success(), HttpStatus.OK);
    }
}