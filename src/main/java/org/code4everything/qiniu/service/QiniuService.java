package org.code4everything.qiniu.service;

import com.qiniu.cdn.CdnResult;
import com.qiniu.common.QiniuException;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.model.BatchStatus;
import com.zhazhapan.util.Checker;
import com.zhazhapan.util.Formatter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;
import org.apache.log4j.Logger;
import org.code4everything.qiniu.QiniuApplication;
import org.code4everything.qiniu.api.SdkManager;
import org.code4everything.qiniu.constant.QiniuValueConsts;
import org.code4everything.qiniu.controller.MainController;
import org.code4everything.qiniu.model.FileBean;
import org.code4everything.qiniu.util.DialogUtils;
import org.code4everything.qiniu.util.QiniuUtils;

import java.util.ArrayList;
import java.util.Map;

/**
 * 七牛服务类
 *
 * @author pantao
 * @since 2018/11/13
 */
public class QiniuService {

    // TODO: 2018/11/13 服务类是否需要弹出错误框？

    private static final Logger LOGGER = Logger.getLogger(QiniuService.class);

    private final SdkManager sdkManager = new SdkManager();

    /**
     * 获取空间文件列表
     */
    public void listFile() {
        MainController main = MainController.getInstance();
        // 列举空间文件列表
        BucketManager.FileListIterator iterator = sdkManager.getFileListIterator(main.bucketChoiceCombo.getValue());
        ArrayList<FileBean> files = new ArrayList<>();
        MainController.totalLength = 0;
        MainController.totalSize = 0;
        // 处理获取的file list结果
        while (iterator.hasNext()) {
            com.qiniu.storage.model.FileInfo[] items = iterator.next();
            for (com.qiniu.storage.model.FileInfo item : items) {
                MainController.totalLength++;
                MainController.totalSize += item.fsize;
                // 将七牛的时间单位（100纳秒）转换成毫秒，然后转换成时间
                String time = Formatter.timeStampToString(item.putTime / 10000);
                String size = Formatter.formatSize(item.fsize);
                FileBean file = new FileBean(item.key, item.mimeType, size, time);
                files.add(file);
            }
        }
        MainController.data = FXCollections.observableArrayList(files);
    }

    /**
     * 批量删除文件，单次批量请求的文件数量不得超过1000
     */
    public void deleteFiles(ObservableList<FileBean> fileBeans, String bucket) {
        if (Checker.isNotEmpty(fileBeans) && QiniuUtils.checkNet()) {
            // 生成待删除的文件列表
            String[] files = new String[fileBeans.size()];
            ArrayList<FileBean> selectedFiles = new ArrayList<>();
            int i = 0;
            for (FileBean fileBean : fileBeans) {
                files[i++] = fileBean.getName();
                selectedFiles.add(fileBean);
            }
            try {
                BatchStatus[] batchStatusList = sdkManager.batchDelete(bucket, files);
                MainController main = MainController.getInstance();
                // 文件列表是否为搜索后结果
                boolean isInSearch = Checker.isNotEmpty(main.searchTextField.getText());
                ObservableList<FileBean> currentRes = main.resTable.getItems();
                // 更新界面数据
                for (i = 0; i < files.length; i++) {
                    BatchStatus status = batchStatusList[i];
                    String file = files[i];
                    if (status.code == 200) {
                        MainController.data.remove(selectedFiles.get(i));
                        MainController.totalLength--;
                        MainController.totalSize -= Formatter.sizeToLong(selectedFiles.get(i).getSize());
                        if (isInSearch) {
                            currentRes.remove(selectedFiles.get(i));
                        }
                    } else {
                        LOGGER.error("delete " + file + " failed, message -> " + status.data.error);
                        DialogUtils.showError("删除文件：" + file + " 失败");
                    }
                }
            } catch (QiniuException e) {
                DialogUtils.showException(QiniuValueConsts.DELETE_ERROR, e);
            }
            MainController.getInstance().setBucketCount();
        }
    }

    /**
     * 修改文件类型
     */
    public boolean changeType(String fileName, String newType, String bucket) {
        boolean result = true;
        try {
            sdkManager.changeMime(bucket, fileName, newType);
        } catch (QiniuException e) {
            DialogUtils.showException(QiniuValueConsts.CHANGE_FILE_TYPE_ERROR, e);
            result = false;
        }
        return result;
    }

    /**
     * 重命名文件
     */
    public boolean renameFile(String bucket, String oldName, String newName) {
        return moveFile(bucket, oldName, bucket, newName);
    }

    /**
     * 移动文件
     */
    private boolean moveFile(String srcBucket, String fromKey, String destBucket, String toKey) {
        return moveOrCopyFile(srcBucket, fromKey, destBucket, toKey, SdkManager.FileAction.MOVE);
    }

    /**
     * 移动或复制文件
     */
    public boolean moveOrCopyFile(String srcBucket, String srcKey, String destBucket, String destKey,
                                  SdkManager.FileAction fileAction) {
        boolean result = true;
        try {
            sdkManager.moveOrCopyFile(srcBucket, srcKey, destBucket, destKey, fileAction);
        } catch (QiniuException e) {
            LOGGER.error("move file failed, message -> " + e.getMessage());
            DialogUtils.showException(QiniuValueConsts.MOVE_OR_RENAME_ERROR, e);
            result = false;
        }
        return result;
    }

    /**
     * 设置文件生存时间
     */
    public void setFilesLife(String bucket, String key, int days) {
        try {
            sdkManager.deleteAfterDays(bucket, key, days);
            LOGGER.info("set file life success");
        } catch (QiniuException e) {
            LOGGER.error("set file life error, message -> " + e.getMessage());
            DialogUtils.showException(QiniuValueConsts.MOVE_OR_RENAME_ERROR, e);
        }
    }

    /**
     * 更新镜像源
     */
    public void updateFiles(String bucket, String key) {
        try {
            sdkManager.prefetch(bucket, key);
            LOGGER.info("prefetch files success");
        } catch (QiniuException e) {
            LOGGER.error("prefetch files error, message -> " + e.getMessage());
            DialogUtils.showException(QiniuValueConsts.UPDATE_ERROR, e);
        }
    }

    /**
     * 公有下载
     */
    public void publicDownload(String fileName, String domain) {
        QiniuUtils.download(QiniuUtils.buildUrl(fileName, domain));
    }

    /**
     * 私有下载
     */
    public void privateDownload(String fileName, String domain) {
        QiniuUtils.download(sdkManager.getPrivateUrl(QiniuUtils.buildUrl(fileName, domain)));
    }

    /**
     * 刷新文件
     */
    public void refreshFiles(ObservableList<FileBean> fileBeans, String domain) {
        if (Checker.isNotEmpty(fileBeans)) {
            String[] files = new String[fileBeans.size()];
            int i = 0;
            // 获取公有链接
            for (FileBean fileBean : fileBeans) {
                files[i++] = QiniuUtils.buildUrl(fileBean.getName(), domain);
            }
            try {
                // 属性文件
                sdkManager.refreshFiles(files);
                LOGGER.info("refresh files success");
            } catch (QiniuException e) {
                LOGGER.error("refresh files error, message -> " + e.getMessage());
                DialogUtils.showException(e);
            }
        }
    }

    /**
     * 日志下载
     */
    public void downloadCdnLog(String logDate) {
        if (Checker.isNotEmpty(QiniuApplication.getConfigBean().getBuckets()) && Checker.isDate(logDate)) {
            // 转换域名成数组格式
            String[] domains = new String[QiniuApplication.getConfigBean().getBuckets().size()];
            for (int i = 0; i < QiniuApplication.getConfigBean().getBuckets().size(); i++) {
                domains[i] = QiniuApplication.getConfigBean().getBuckets().get(i).getUrl();
            }
            Map<String, CdnResult.LogData[]> cdnLog = null;
            try {
                cdnLog = sdkManager.listCdnLog(domains, logDate);
            } catch (QiniuException e) {
                DialogUtils.showException(e);
            }
            if (Checker.isNotEmpty(cdnLog)) {
                // 下载日志
                for (Map.Entry<String, CdnResult.LogData[]> logs : cdnLog.entrySet()) {
                    for (CdnResult.LogData log : logs.getValue()) {
                        QiniuUtils.download(log.url);
                    }
                }
            }
        }
    }


    /**
     * 获取空间带宽统计，使用自定义单位
     */
    public XYChart.Series<String, Long> getBucketBandwidth(String[] domains, String startDate, String endDate,
                                                           String unit) {
        // 获取带宽数据
        CdnResult.BandwidthResult bandwidthResult = null;
        try {
            bandwidthResult = sdkManager.getBandwidthData(domains, startDate, endDate);
        } catch (QiniuException e) {
            Platform.runLater(() -> DialogUtils.showException(QiniuValueConsts.BUCKET_BAND_ERROR, e));
        }
        // 设置图表
        XYChart.Series<String, Long> series = new XYChart.Series<>();
        series.setName(QiniuValueConsts.BUCKET_BANDWIDTH_COUNT.replaceAll("[A-Z]+", unit));
        // 格式化数据
        if (Checker.isNotNull(bandwidthResult) && Checker.isNotEmpty(bandwidthResult.data)) {
            long unitSize = Formatter.sizeToLong("1 " + unit);
            for (Map.Entry<String, CdnResult.BandwidthData> bandwidth : bandwidthResult.data.entrySet()) {
                CdnResult.BandwidthData bandwidthData = bandwidth.getValue();
                if (Checker.isNotNull(bandwidthData)) {
                    setSeries(bandwidthResult.time, bandwidthData.china, bandwidthData.oversea, series, unitSize);
                }
            }
        }
        return series;
    }

    /**
     * 获取空间的流量统计，使用自定义单位
     */
    public XYChart.Series<String, Long> getBucketFlux(String[] domains, String startDate, String endDate, String unit) {
        // 获取流量数据
        CdnResult.FluxResult fluxResult = null;
        try {
            fluxResult = sdkManager.getFluxData(domains, startDate, endDate);
        } catch (QiniuException e) {
            Platform.runLater(() -> DialogUtils.showException(QiniuValueConsts.BUCKET_FLUX_ERROR, e));
        }
        // 设置图表
        XYChart.Series<String, Long> series = new XYChart.Series<>();
        series.setName(QiniuValueConsts.BUCKET_FLUX_COUNT.replaceAll("[A-Z]+", unit));
        // 格式化数据
        if (Checker.isNotNull(fluxResult) && Checker.isNotEmpty(fluxResult.data)) {
            long unitSize = Formatter.sizeToLong("1 " + unit);
            for (Map.Entry<String, CdnResult.FluxData> flux : fluxResult.data.entrySet()) {
                CdnResult.FluxData fluxData = flux.getValue();
                if (Checker.isNotNull(fluxData)) {
                    setSeries(fluxResult.time, fluxData.china, fluxData.oversea, series, unitSize);
                }
            }
        }
        return series;
    }

    /**
     * 处理带宽数据
     */
    private void setSeries(String[] times, Long[] china, Long[] oversea, XYChart.Series<String, Long> series,
                           long unit) {
        int i = 0;
        for (String time : times) {
            long size = 0;
            if (Checker.isNotEmpty(china)) {
                size += china[i];
            }
            if (Checker.isNotEmpty(oversea)) {
                size += oversea[i];
            }
            series.getData().add(new XYChart.Data<>(time.substring(5, 10), size / unit));
            i++;
        }
    }
}
