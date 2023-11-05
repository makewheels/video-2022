package com.github.makewheels.video2022.finance.fee.ossaccess;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.file.access.FileAccessLog;
import com.github.makewheels.video2022.finance.bill.Bill;
import com.github.makewheels.video2022.finance.fee.base.FeeRepository;
import com.github.makewheels.video2022.finance.fee.base.FeeStatus;
import com.github.makewheels.video2022.finance.fee.base.FeeTypeEnum;
import com.github.makewheels.video2022.finance.unitprice.UnitName;
import com.github.makewheels.video2022.finance.unitprice.UnitPrice;
import com.github.makewheels.video2022.finance.unitprice.UnitPriceService;
import com.github.makewheels.video2022.finance.wallet.Wallet;
import com.github.makewheels.video2022.finance.wallet.WalletRepository;
import com.github.makewheels.video2022.user.UserRepository;
import com.github.makewheels.video2022.user.bean.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

/**
 * OSS访问文件费用服务
 */
@Service
@Slf4j
public class OssAccessFeeService {
    @Resource
    private UnitPriceService unitPriceService;
    @Resource
    private FeeRepository feeRepository;
    @Resource
    private UserRepository userRepository;
    @Resource
    private WalletRepository walletRepository;
    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * 计费
     */
    public OssAccessFee create(FileAccessLog fileAccessLog, HttpServletRequest request,
                               String videoId, String clientId, String sessionId,
                               String resolution, String fileId) {
        OssAccessFee accessFee = new OssAccessFee();
        accessFee.setUserId(fileAccessLog.getUserId());
        accessFee.setVideoId(videoId);

        accessFee.setFileId(fileId);
        accessFee.setAccessId(fileAccessLog.getId());
        accessFee.setKey(fileAccessLog.getKey());
        accessFee.setStorageClass(fileAccessLog.getStorageClass());
        accessFee.setFileSize(fileAccessLog.getSize());
        accessFee.setBillTime(fileAccessLog.getCreateTime());

        accessFee.setFeeType(FeeTypeEnum.OSS_ACCESS.getCode());
        accessFee.setFeeTypeName(FeeTypeEnum.OSS_ACCESS.getName());
        accessFee.setUnitName(UnitName.GB);
        UnitPrice unitPrice = unitPriceService.getOssAccessUnitPrice(fileAccessLog.getCreateTime());
        accessFee.setUnitPrice(unitPrice.getUnitPrice());
        accessFee.setAmount(BigDecimal.valueOf(fileAccessLog.getSize()));
        BigDecimal feePrice = accessFee.getUnitPrice()
                .multiply(accessFee.getAmount())
                .setScale(UnitPriceService.SCALE, RoundingMode.HALF_DOWN)
                .abs();
        accessFee.setFeePrice(feePrice);

        return accessFee;
    }

    /**
     * 将OSS访问文件费用转换为账单
     */
    private Bill convertOssAccessFeeToBill(User user, List<OssAccessFee> accessFees) {
        log.info("用户id={}, 查到 {} 条OSS访问费用", user.getId(), accessFees.size());
        BigDecimal originChargePrice = accessFees.stream()
                .map(OssAccessFee::getFeePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(UnitPriceService.SCALE, RoundingMode.HALF_DOWN)
                .abs();

        Bill bill = new Bill();
        bill.setUserId(user.getId());

        bill.setOriginChargePrice(originChargePrice);
        // 小数点后两位抹零
        // 计算抹零金额
        BigDecimal roundDownPrice = originChargePrice
                .setScale(2, RoundingMode.HALF_DOWN)
                .subtract(originChargePrice)
                .abs();
        bill.setRoundDownPrice(roundDownPrice);
        // 应付金额
        BigDecimal realChargePrice = originChargePrice.subtract(roundDownPrice)
                .setScale(2, RoundingMode.HALF_DOWN);
        bill.setRealChargePrice(realChargePrice);

        bill.setChargeTime(new Date());
        Wallet wallet = walletRepository.findByUserId(user.getId());
        if (wallet != null) {
            bill.setWalletId(wallet.getId());
        }
        bill.setFeeCount(accessFees.size());
        return bill;
    }

    /**
     * 创建账单
     * 按用户分组，多个视频汇总到一个账单
     */
    public void createBill(Date billTimeStart, Date billTimeEnd) {
        for (User user : userRepository.listAll()) {
            List<OssAccessFee> accessFees = feeRepository.listDirectDeductionFee(
                    OssAccessFee.class, billTimeStart, billTimeEnd, user.getId(), FeeStatus.CREATED);
            if (CollectionUtils.isEmpty(accessFees)) {
                log.info("用户id：{}，没有OSS访问费用，跳过", user.getId());
                continue;
            }
            Bill bill = convertOssAccessFeeToBill(user, accessFees);
            log.info("创建OSS访问账单，用户id：{}，账单：{}", user.getId(), JSON.toJSONString(bill));
            mongoTemplate.save(bill);

            // 反向关联：计费的账单id
            for (OssAccessFee accessFee : accessFees) {
                feeRepository.updateBillId(OssAccessFee.class, accessFee.getId(), bill.getId());
            }

            // 更新费用状态
            for (OssAccessFee accessFee : accessFees) {
                feeRepository.updateStatus(OssAccessFee.class, accessFee.getId(), FeeStatus.BILLED);
            }
        }
    }

}
