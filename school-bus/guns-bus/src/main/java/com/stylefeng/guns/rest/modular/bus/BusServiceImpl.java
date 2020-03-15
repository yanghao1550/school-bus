/**
 * @program school-bus
 * @description: BusServiceIml
 * @author: mf
 * @create: 2020/03/01 16:26
 */

package com.stylefeng.guns.rest.modular.bus;

import com.alibaba.dubbo.config.annotation.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stylefeng.guns.core.util.DateUtil;
import com.stylefeng.guns.rest.bus.IBusService;
import com.stylefeng.guns.rest.bus.dto.*;
import com.stylefeng.guns.rest.common.constants.RetCodeConstants;
import com.stylefeng.guns.rest.common.persistence.dao.BusMapper;
import com.stylefeng.guns.rest.common.persistence.dao.CountMapper;
import com.stylefeng.guns.rest.common.persistence.model.Bus;
import com.stylefeng.guns.rest.common.persistence.model.Count;
import com.stylefeng.guns.rest.modular.bus.converter.BusConverter;
import com.stylefeng.guns.rest.modular.bus.converter.CountConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Component
@Service
public class BusServiceImpl implements IBusService {

    @Autowired
    private BusMapper busMapper;
    @Autowired
    private CountMapper countMapper;
    @Autowired
    private BusConverter busConverter;
    @Autowired
    private CountConverter countConverter;

    @Override
    public PageBusResponse getBus(PageBusRequest request) {
        PageBusResponse response = new PageBusResponse();
        try {
            IPage<Bus> busIPage = new Page<>(request.getCurrentPage(), request.getPageSize());
            busIPage = busMapper.selectPage(busIPage, null);
            response.setCurrentPage(busIPage.getCurrent());
            response.setPageSize(busIPage.getSize());
            response.setPages(busIPage.getPages());
            response.setTotal(busIPage.getTotal());
            response.setBusDtos(busConverter.bus2List(busIPage.getRecords()));
            response.setCode(RetCodeConstants.SUCCESS.getCode());
            response.setMsg(RetCodeConstants.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            response.setCode(RetCodeConstants.DB_EXCEPTION.getCode());
            response.setMsg(RetCodeConstants.DB_EXCEPTION.getMessage());
            log.error("getBus:" , e);
            return response;
        }
        return response;
    }

    @Override
    public PageCountResponse getCount(PageCountRequest request) {
        PageCountResponse response = new PageCountResponse();
        try {
            IPage<CountSimpleDto> countIPage = new Page<>(request.getCurrentPage(), request.getPageSize());
            QueryWrapper<CountSimpleDto> queryWrapper = new QueryWrapper<>();
            // 获取时间
            String currHours = DateUtil.getHours();
            System.out.println("当前时间："+currHours);
            // 判断条件
            queryWrapper
                    .ge("begin_time", currHours) // 时间
                    .and(o -> o.eq("bus_status", request.getBusStatus()));

            countIPage = countMapper.selectCounts(countIPage, queryWrapper);
            response.setCurrentPage(countIPage.getCurrent());
            response.setPageSize(countIPage.getSize());
            response.setPages(countIPage.getPages());
            response.setTotal(countIPage.getTotal());
            response.setCountSimpleDtos(countIPage.getRecords());
            response.setCode(RetCodeConstants.SUCCESS.getCode());
            response.setMsg(RetCodeConstants.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            response.setCode(RetCodeConstants.DB_EXCEPTION.getCode());
            response.setMsg(RetCodeConstants.DB_EXCEPTION.getMessage());
            log.error("getCount:", e);
            return response;
        }
        return response;
    }

    @Override
    public CountDetailResponse getCountDetailById(CountDetailRequest request) {
        CountDetailResponse response = new CountDetailResponse();
        try {
            QueryWrapper<CountDetailDto> wrapper = new QueryWrapper<>();
            wrapper.eq("sc.uuid", request.getCountId());
            CountDetailDto countDetailDto = countMapper.selectCountDetailById(wrapper);
            response.setCountDetailDto(countDetailDto);
            response.setCode(RetCodeConstants.SUCCESS.getCode());
            response.setMsg(RetCodeConstants.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            log.error("getCountDetail", e);
            response.setCode(RetCodeConstants.DB_EXCEPTION.getCode());
            response.setMsg(RetCodeConstants.DB_EXCEPTION.getMessage());
            return response;
        }
        return response;
    }

    @Override
    public boolean selectedSeats(String seats, Integer coundId) {
        // 查查数据库， 找到座位字段
        boolean b = false; // false:不重复，true：重复
        try {
            Count count = countMapper.selectById(coundId);
            b = repeatSeats(seats, count.getSelectedSeats());
        } catch (Exception e) {
            e.printStackTrace();
            log.error("selectedSeats", e);
            return true; // 异常就算是重复
        }
        return b;
    }

    @Override
    public boolean updateSeats(String seats, Integer coundId) {
        // 直接找场次的座位
        try {
            Count count = countMapper.selectById(coundId);
            String selectedSeats = count.getSelectedSeats();
            String newSelectedSeats = selectedSeats + "," + seats; // 这里可以优化，字符串拼接，这样的方式爆内存
            count.setSelectedSeats(newSelectedSeats);
            countMapper.updateById(count);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("updateSeats", e);
            return false;
        }
        return true;
    }

    private boolean repeatSeats(String selectedSeats, String currDbSeats) {
        // 比如，selectedSeats 是1,2
        // dbSeats：""，
        // dbSeats："1,2,3"，
        // dbSeats: "4,5"
        // 前端传来的selectedSeats， 前端判断是否为空，要不然后端也判断一下得了
        if (selectedSeats.equals("")) {
            return true;
        }
        if (currDbSeats.equals("")) {
            return false;
        }
        String[] ss = selectedSeats.split(",");
        String[] cs = currDbSeats.split(",");
        HashSet<String> hashSet = new HashSet<>(Arrays.asList(cs)); // 这步存在并发问题 值得优化的地方
        for (String s : ss) {
            if (hashSet.contains(s)) return true;
        }
        return false;
    }

    /**
     * 私有，谁也无法访问
     */
    @Scheduled(cron = "0 0/30 7-21 * * ?") // 每天上午7点到晚上21点，每隔30分钟执行一次
    private void schedulChangeBusStatus() {
        // 获取
        String currTime = DateUtil.getHours();
        log.warn("schedulChangeBusStatus->目前时间：" + currTime);
        System.out.println("目前时间:"+ currTime);
        QueryWrapper<Count> queryWrapper = new QueryWrapper<>();
        // 先取出beingtime和now相等的表或者end_time和now相等到表
        queryWrapper
                .eq("begin_time", currTime)
                .or()
                .eq("end_time", currTime);
        List<Count> counts = countMapper.selectList(queryWrapper);
        log.warn("schedulChangeBusStatus->查询到的：" + counts.toString());
//        System.out.println("查询到的:"+counts.toString());
        // 开始作妖
        for (Count count : counts) {
            String busStatus = count.getBusStatus();
            String beginTime = count.getBeginTime();
            String endTime = count.getEndTime();
            if (currTime.equals(beginTime)) {
                if (busStatus.equals("0")) { // 沙河空闲
                    count.setBusStatus("2"); // 沙河->清水河
                }
                if (busStatus.equals("1")) { // 清水河空闲
                    count.setBusStatus("3"); // 清水河->沙河
                }
                count.setSelectedSeats(""); // 清空座位
            }
            if (currTime.equals(endTime)) {
                if (busStatus.equals("2")) { // 沙河->清水河
                    count.setBusStatus("1"); // 清水河空闲
                }
                if (busStatus.equals("3")) { // 清水河->沙河
                    count.setBusStatus("0"); // 沙河空闲
                }
            }
            System.out.println("修改的：" + count);
            log.warn("schedulChangeBusStatus->修改的：" + count);
            // 写入数据库
            countMapper.updateById(count);
        }
    }
}
