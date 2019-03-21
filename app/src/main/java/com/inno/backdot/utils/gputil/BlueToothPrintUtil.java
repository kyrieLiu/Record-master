package com.inno.backdot.utils.gputil;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import com.gprinter.command.EscCommand;
import com.gprinter.command.LabelCommand;
import com.inno.backdot.utils.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.Vector;

import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED;



public class BlueToothPrintUtil {


    private static BlueToothPrintUtil mAidlUtil = new BlueToothPrintUtil() {
    };
    private Context context;

    private String mediumSpline;
    private int largeSize, row1, row2, row3, contentSize, titleSize, lineNumber,four_row1,four_row2,four_row3;

    private static final String TAG = "AidlUtil";


    private OutputStream mOutputStream = null;
    private int id = 0;

    private DeviceConnFactoryManager deviceManager;
    private boolean haveRegist=false;


    private BlueToothPrintUtil() {
        largeSize = 14 * 2;
        mediumSpline = "-------------------------------";
        row1 = 18;
        row2 = 5;
        row3 = 8;
        titleSize = 35;
        contentSize = 24;
        lineNumber = 7;

        four_row1 = 11;
        four_row2 = 3;
        four_row3 = 10;
    }

    public static BlueToothPrintUtil getInstance() {
        return mAidlUtil;
    }

    /**
     * 连接服务
     *
     * @param context context
     */
    public void connectPrinterService(Context context) {
        this.context = context;

        IntentFilter filter = new IntentFilter(Constant.ACTION_USB_PERMISSION);
        filter.addAction(ACTION_USB_DEVICE_DETACHED);
        filter.addAction(Constant.ACTION_QUERY_PRINTER_STATE);
        filter.addAction(DeviceConnFactoryManager.ACTION_CONN_STATE);
        this.context.registerReceiver(receiver, filter);
        haveRegist=true;

        Set<BluetoothDevice> pairedDevices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (final BluetoothDevice device : pairedDevices) {
                int deviceType = device.getBluetoothClass().getMajorDeviceClass();
                Log.i("info", "已建立配对的设备类型" + deviceType + "名称==" + device.getName() + "  ID==" + device.getAddress());
                String deviceKey=device.getName().substring(0,3);

                if ("MHT".equals(deviceKey)) {
                            new DeviceConnFactoryManager.Build()
                                    .setId(0)
                                    //设置连接方式
                                    .setConnMethod(DeviceConnFactoryManager.CONN_METHOD.BLUETOOTH)
                                    //设置连接的蓝牙mac地址
                                    .setMacAddress(device.getAddress())
                                    .build();
                            //打开端口
                            deviceManager =DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id];

                            if (deviceManager !=null) deviceManager.openPort();
                        }
            }
        }
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case Constant.ACTION_USB_PERMISSION:
                    synchronized (this) {
                        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            if (device != null) {
                                System.out.println("permission ok for device " + device);
                                //usbConn(device);
                            }
                        } else {
                            System.out.println("permission denied for device " + device);
                        }
                    }
                    break;
                //Usb连接断开、蓝牙连接断开广播
                case ACTION_USB_DEVICE_DETACHED:
                case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                    if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] != null) {
                        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].closePort(id);
                    }
                    break;
                case DeviceConnFactoryManager.ACTION_CONN_STATE:
                    int state = intent.getIntExtra(DeviceConnFactoryManager.STATE, -1);
                    int deviceId = intent.getIntExtra(DeviceConnFactoryManager.DEVICE_ID, -1);
                    switch (state) {
                        case DeviceConnFactoryManager.CONN_STATE_DISCONNECT:
                            if (id == deviceId) {
                                Logger.d("连接状态：未连接");
                                //tvConnState.setText(getString(R.string.str_conn_state_disconnect));
                            }
                            break;
                        case DeviceConnFactoryManager.CONN_STATE_CONNECTING:
                            Logger.d("连接状态：连接中");
                            //tvConnState.setText(getString(R.string.str_conn_state_connecting));
                            break;
                        case DeviceConnFactoryManager.CONN_STATE_CONNECTED:
                            Logger.d("连接状态：已连接");
                            Toast.makeText(context,"蓝牙打印机连接成功",Toast.LENGTH_SHORT).show();
                            //tvConnState.setText(getString(R.string.str_conn_state_connected) + "\n" + getConnDeviceInfo());
                            break;
                        case DeviceConnFactoryManager.CONN_STATE_FAILED:
                            Logger.d("连接状态：连接失败");
                            Toast.makeText(context,"蓝牙打印机连接失败,请检查蓝牙连接",Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            break;
                    }
                    break;
                default:
                    break;
            }
        }
    };



    public void printLabel(Bitmap b){
        if(deviceManager==null)return;
        if (deviceManager.getCurrentPrinterCommand()== PrinterCommand.ESC){
            Toast.makeText(context,"请选择正确的打印机指令",Toast.LENGTH_SHORT).show();
            return;
        }
        LabelCommand tsc = new LabelCommand();
        // 设置标签尺寸，按照实际尺寸设置
        tsc.addSize(55, 30);
        // 设置标签间隙，按照实际尺寸设置，如果为无间隙纸则设置为0
        tsc.addGap(1);
        // 设置打印方向
        tsc.addDirection(LabelCommand.DIRECTION.FORWARD, LabelCommand.MIRROR.NORMAL);
        // 开启带Response的打印，用于连续打印
        tsc.addQueryPrinterStatus(LabelCommand.RESPONSE_MODE.ON);
        // 设置原点坐标
        tsc.addReference(0, 0);
        // 撕纸模式开启
        tsc.addTear(EscCommand.ENABLE.ON);
        // 清除打印缓冲区
        tsc.addCls();

        // 绘制图片
        //tsc.addBitmap(20, 50, LabelCommand.BITMAP_MODE.OVERWRITE, b.getWidth(), b);

        // 绘制简体中文
        tsc.addBitmap(50, 25, LabelCommand.BITMAP_MODE.OVERWRITE, b.getWidth(), b);
        String title="卡兹乐天然狗罐金枪鱼+鱼片";
        String price="价格: 22.30";
        int sizeTitle = largeSize - length(title);
        // 文字居中需要在前面补足相应空格，后面可以用换行符换行
        String titleStr = getBlankBySize((int) (sizeTitle / 2d)) + title;
        int priceSize=largeSize-length(price);
        String priceStr=getBlankBySize((int) (priceSize / 2d)) + price;
        // 绘制简体中文
        tsc.addText(60, 162, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1,
                titleStr);
        tsc.addText(60, 200, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1,
                priceStr);
        //tsc.addQRCode(250, 80, LabelCommand.EEC.LEVEL_L, 5, LabelCommand.ROTATION.ROTATION_0, " www.smarnet.cc");
        // 绘制一维条码
        //tsc.add1DBarcode(20, 250, LabelCommand.BARCODETYPE.CODE128, 100, LabelCommand.READABEL.EANBEL, LabelCommand.ROTATION.ROTATION_0, "SMARNET");
        // 打印标签份数
        tsc.addPrint(1);
        // 打印标签后 蜂鸣器响

        //tsc.addSound(2, 100);
        //tsc.addCashdrwer(LabelCommand.FOOT.F5, 255, 255);
        Vector<Byte> datas = tsc.getCommand();
        // 发送数据
        if (deviceManager == null) {
            return;
        }
        deviceManager.sendDataImmediately(datas);
    }


    /**
     * 发送票据
     */
    public void sendReceiptWithResponse() {
        EscCommand esc = new EscCommand();
        esc.addInitializePrinter();
        // 设置打印居中
        esc.addSelectJustification(EscCommand.JUSTIFICATION.CENTER);
        // 设置为倍高倍宽
        esc.addSelectPrintModes(EscCommand.FONT.FONTA, EscCommand.ENABLE.OFF, EscCommand.ENABLE.ON, EscCommand.ENABLE.ON, EscCommand.ENABLE.OFF);
        // 打印文字
        esc.addText("Sample\n");
        esc.addPrintAndLineFeed();

		/* 打印文字 */
        // 取消倍高倍宽
        esc.addSelectPrintModes(EscCommand.FONT.FONTA, EscCommand.ENABLE.OFF, EscCommand.ENABLE.OFF, EscCommand.ENABLE.OFF, EscCommand.ENABLE.OFF);
        // 设置打印左对齐
        esc.addSelectJustification(EscCommand.JUSTIFICATION.LEFT);
        // 打印文字
        esc.addText("Print text\n");
        // 打印文字
        esc.addText("Welcome to use SMARNET printer!\n");

		/* 打印繁体中文 需要打印机支持繁体字库 */
        String message = "佳博智匯票據打印機\n";
        esc.addText(message, "GB2312");
        esc.addPrintAndLineFeed();

		/* 绝对位置 具体详细信息请查看GP58编程手册 */
        esc.addText("智汇");
        esc.addSetHorAndVerMotionUnits((byte) 7, (byte) 0);
        esc.addSetAbsolutePrintPosition((short) 6);
        esc.addText("网络");
        esc.addSetAbsolutePrintPosition((short) 10);
        esc.addText("设备");
        esc.addPrintAndLineFeed();

		/* 打印图片 */
        // 打印文字
        esc.addText("Print bitmap!\n");
        // 打印图片
        //esc.addOriginRastBitImage(b, 384, 0);

		/* 打印一维条码 */
        // 打印文字
        esc.addText("Print code128\n");
        esc.addSelectPrintingPositionForHRICharacters(EscCommand.HRI_POSITION.BELOW);
        // 设置条码可识别字符位置在条码下方
        // 设置条码高度为60点
        esc.addSetBarcodeHeight((byte) 60);
        // 设置条码单元宽度为1
        esc.addSetBarcodeWidth((byte) 1);
        // 打印Code128码
        esc.addCODE128(esc.genCodeB("SMARNET"));
        esc.addPrintAndLineFeed();

		/*
         * QRCode命令打印 此命令只在支持QRCode命令打印的机型才能使用。 在不支持二维码指令打印的机型上，则需要发送二维条码图片
		 */
        // 打印文字
        esc.addText("Print QRcode\n");
        // 设置纠错等级
        esc.addSelectErrorCorrectionLevelForQRCode((byte) 0x31);
        // 设置qrcode模块大小
        esc.addSelectSizeOfModuleForQRCode((byte) 3);
        // 设置qrcode内容
        esc.addStoreQRCodeData("www.smarnet.cc");
        esc.addPrintQRCode();// 打印QRCode
        esc.addPrintAndLineFeed();

        // 设置打印左对齐
        esc.addSelectJustification(EscCommand.JUSTIFICATION.CENTER);
        //打印文字
        esc.addText("Completed!\r\n");

        // 开钱箱
        esc.addGeneratePlus(LabelCommand.FOOT.F5, (byte) 255, (byte) 255);
        esc.addPrintAndFeedLines((byte) 8);
        // 加入查询打印机状态，打印完成后，此时会接收到GpCom.ACTION_DEVICE_STATUS广播
        esc.addQueryPrinterStatus();
        Vector<Byte> datas = esc.getCommand();
        // 发送数据
        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately(datas);
    }


    /**
     * 打印单件退款单
     *
     */
    public void printRefoundItemInformation(DeviceConnFactoryManager deviceManager) {
        Log.d("tag","开始打印");
        if (deviceManager==null)return;
        if (deviceManager.getCurrentPrinterCommand()== PrinterCommand.TSC){
            Toast.makeText(context,"请选择正确的打印机指令",Toast.LENGTH_SHORT).show();
            return;
        }
        String title = "单品退款单";
        EscCommand esc = new EscCommand();
        esc.addInitializePrinter();
        esc.addSelectJustification(EscCommand.JUSTIFICATION.CENTER); // 设置打印居中
        // 设置为倍高倍宽
        esc.addSelectPrintModes(EscCommand.FONT.FONTA, EscCommand.ENABLE.OFF, EscCommand.ENABLE.ON, EscCommand.ENABLE.ON, EscCommand.ENABLE.OFF);
        esc.addText(title+"\n\n");
        StringBuilder sbTable = new StringBuilder();
        sbTable.append("商家名称: 京东自营\n");
        String moneyStr = "退款金额: 20元";
        sbTable.append(moneyStr + "\n");

            sbTable.append("退款方式: 线上支付\n");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String time = format.format(new Date());
        sbTable.append("退款时间: " + time + "\n");
        sbTable.append("交易单号: 201825475211\n");
        esc.addSelectPrintModes(EscCommand.FONT.FONTA, EscCommand.ENABLE.OFF, EscCommand.ENABLE.OFF, EscCommand.ENABLE.OFF, EscCommand.ENABLE.OFF);
        esc.addSelectJustification(EscCommand.JUSTIFICATION.LEFT);
        esc.addText(sbTable.toString()+"\n\n\n\n");
        Vector<Byte> datas = esc.getCommand();
        deviceManager.sendDataImmediately(datas);
    }

    /**
     * 单行打印文字
     *
     * @param content     打印的内容
     * @param fontSize    字体大小
     * @param isUnderline 是否加下划线
     */
    public void printAgreement(String content, int fontSize, boolean isUnderline, boolean isBoldOn) {
        if (deviceManager==null)return;
        if (deviceManager.getCurrentPrinterCommand()== PrinterCommand.TSC){
            Toast.makeText(context,"请选择正确的打印机指令",Toast.LENGTH_SHORT).show();
            return;
        }
        EscCommand esc = new EscCommand();
        esc.addInitializePrinter();
        esc.addText(content+"\n");
        Vector<Byte> datas = esc.getCommand();
        deviceManager.sendDataImmediately(datas);

    }

    /**
     * 打印标题title
     *
     * @param title
     */
    public void printTitle(String title) {
        if (deviceManager==null)return;
        if (deviceManager.getCurrentPrinterCommand()== PrinterCommand.TSC){
            Toast.makeText(context,"请选择正确的打印机指令",Toast.LENGTH_SHORT).show();
            return;
        }
        EscCommand esc = new EscCommand();
        esc.addInitializePrinter();
        esc.addSelectJustification(EscCommand.JUSTIFICATION.CENTER); // 设置打印居中
        esc.addSelectPrintModes(EscCommand.FONT.FONTA, EscCommand.ENABLE.OFF, EscCommand.ENABLE.ON, EscCommand.ENABLE.ON, EscCommand.ENABLE.OFF);
        esc.addText(title+"\n\n");
        Vector<Byte> datas = esc.getCommand();
        deviceManager.sendDataImmediately(datas);


    }

    /**
     * 打印均分字符串
     *
     * @param part1 字符串一
     * @param part2 字符串二
     */
    public void printPartTwo(String part1, String part2, int fontSize, boolean isUnderline) {
        if (deviceManager==null)return;
        if (deviceManager.getCurrentPrinterCommand()== PrinterCommand.TSC){
            Toast.makeText(context,"请选择正确的打印机指令",Toast.LENGTH_SHORT).show();
            return;
        }
        String str = part1 + getBlankBySize(10 - length(part1)) + part2;
        EscCommand esc = new EscCommand();
        esc.addInitializePrinter();
        esc.addText(str+"\n");
        Vector<Byte> datas = esc.getCommand();
        deviceManager.sendDataImmediately(datas);

    }


    private StringBuilder getBuilder(StringBuilder builder, String name, double money, int count) {
        double nameSize = length(name);
        if (nameSize > row1) {
            // 列内容长度大于最大列长度,当成一行内容（换行）
            builder.append(name + "\n");
            // 数量和价格不会超过最大列宽，就不判断内容是否超出了
            String newLineSecond = money + "元" + getBlankBySize(row2 - length(count + ""));
            String newLineEnd = money + "\n";
            String newLineAll = newLineSecond + newLineEnd;
            // 左边补足row1长度空格
            builder.append(getBlankBySize(row1) + newLineAll);
        } else {
            // 正常
            String rowFirst = name + getBlankBySize(row1 - length(name));
            String rowSecond = count + getBlankBySize(row2 - length(count + ""));
            // 最后直接换行就可以了
            String rowEnd = money + "元\n";
            builder.append(rowFirst + rowSecond + rowEnd);
        }
        return builder;
    }

    private String getBlankBySize(int size) {
        String resultStr = "";
        for (int i = 0; i < size; i++) {
            resultStr += " ";
        }
        return resultStr;
    }

    private int length(String s) {
        if (s == null)
            return 0;
        char[] c = s.toCharArray();
        int len = 0;
        for (int i = 0; i < c.length; i++) {
            len++;
            if (!isLetter(c[i])) {
                len++;
            }
        }
        return len;
    }

    private boolean isLetter(char c) {
        int k = 0x80;
        return c / k == 0 ? true : false;
    }

    public BigDecimal add(BigDecimal b1, double v2) {
        BigDecimal b2 = new BigDecimal(Double.toString(v2));
        return b1.add(b2);
    }

    public final static int WIDTH_PIXEL = 384;
    public final static int IMAGE_SIZE = 320;
    public void printBitmap(Bitmap bmp) {
        if (mOutputStream==null)return;
        //bmp = compressPic(bmp);
        byte[] bmpByteArray = draw2PxPoint(bmp);
        printRawBytes(bmpByteArray);
    }

    /**
     * 对图片进行压缩（去除透明度）
     *
     * @param bitmapOrg
     */
    private Bitmap compressPic(Bitmap bitmapOrg) {
        // 获取这个图片的宽和高
        int width = bitmapOrg.getWidth();
        int height = bitmapOrg.getHeight();
        // 定义预转换成的图片的宽度和高度
        int newWidth = IMAGE_SIZE;
        int newHeight = IMAGE_SIZE;
        Bitmap targetBmp = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
        Canvas targetCanvas = new Canvas(targetBmp);
        targetCanvas.drawColor(0xffffffff);
        targetCanvas.drawBitmap(bitmapOrg, new Rect(0, 0, width, height), new Rect(0, 0, newWidth, newHeight), null);
        return targetBmp;
    }

    /*************************************************************************
     * 假设一个360*360的图片，分辨率设为24, 共分15行打印 每一行,是一个 360 * 24 的点阵,y轴有24个点,存储在3个byte里面。
     * 即每个byte存储8个像素点信息。因为只有黑白两色，所以对应为1的位是黑色，对应为0的位是白色
     **************************************************************************/
    private byte[] draw2PxPoint(Bitmap bmp) {
        //先设置一个足够大的size，最后在用数组拷贝复制到一个精确大小的byte数组中
        int size = bmp.getWidth() * bmp.getHeight() / 8 + 10000;
        byte[] tmp = new byte[size];
        int k = 0;
        // 设置行距为0
        tmp[k++] = 0x1B;
        tmp[k++] = 0x33;
        tmp[k++] = 0x00;
        // 居中打印
        tmp[k++] = 0x1B;
        tmp[k++] = 0x61;
        tmp[k++] = 1;
        for (int j = 0; j < bmp.getHeight() / 24f; j++) {
            tmp[k++] = 0x1B;
            tmp[k++] = 0x2A;// 0x1B 2A 表示图片打印指令
            tmp[k++] = 33; // m=33时，选择24点密度打印
            tmp[k++] = (byte) (bmp.getWidth() % 256); // nL
            tmp[k++] = (byte) (bmp.getWidth() / 256); // nH
            for (int i = 0; i < bmp.getWidth(); i++) {
                for (int m = 0; m < 3; m++) {
                    for (int n = 0; n < 8; n++) {
                        byte b = px2Byte(i, j * 24 + m * 8 + n, bmp);
                        tmp[k] += tmp[k] + b;
                    }
                    k++;
                }
            }
            tmp[k++] = 10;// 换行
        }
        // 恢复默认行距
        tmp[k++] = 0x1B;
        tmp[k++] = 0x32;

        byte[] result = new byte[k];
        System.arraycopy(tmp, 0, result, 0, k);
        return result;
    }

    /**
     * 图片二值化，黑色是1，白色是0
     *
     * @param x   横坐标
     * @param y   纵坐标
     * @param bit 位图
     * @return
     */
    private byte px2Byte(int x, int y, Bitmap bit) {
        if (x < bit.getWidth() && y < bit.getHeight()) {
            byte b;
            int pixel = bit.getPixel(x, y);
            int red = (pixel & 0x00ff0000) >> 16; // 取高两位
            int green = (pixel & 0x0000ff00) >> 8; // 取中两位
            int blue = pixel & 0x000000ff; // 取低两位
            int gray = RGB2Gray(red, green, blue);
            if (gray < 128) {
                b = 1;
            } else {
                b = 0;
            }
            return b;
        }
        return 0;
    }

    /**
     * 图片灰度的转化
     */
    private int RGB2Gray(int r, int g, int b) {
        int gray = (int) (0.29900 * r + 0.58700 * g + 0.11400 * b); // 灰度转化公式
        return gray;
    }

    public void printRawBytes(byte[] bytes) {
        try {
            mOutputStream.write(bytes);
            mOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


public void onDestroy(){
        if (haveRegist){
            context.unregisterReceiver(receiver);
            haveRegist=false;
        }
    DeviceConnFactoryManager.closeAllPort();
//    if (threadPool != null) {
//        threadPool.stopThreadPool();
//    }
}

}
