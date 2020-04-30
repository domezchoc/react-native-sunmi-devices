package com.sunmi.innerprinter;

import android.content.BroadcastReceiver;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.Promise;
import android.widget.Toast;

import java.util.Map;
import java.io.IOException;

import woyou.aidlservice.jiuiv5.IWoyouService;
import woyou.aidlservice.jiuiv5.ICallback;
import android.os.RemoteException;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Base64;
import android.graphics.Bitmap;

import java.nio.charset.StandardCharsets;

import android.util.Log;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import android.content.IntentFilter;

import java.util.Map;
import java.util.HashMap;

import com.sunmi.peripheral.printer.ExceptionConst;
import com.sunmi.peripheral.printer.InnerLcdCallback;
import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterException;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.InnerResultCallbcak;
import com.sunmi.peripheral.printer.SunmiPrinterService;
import com.sunmi.peripheral.printer.WoyouConsts;

public class SunmiInnerPrinterModule extends ReactContextBaseJavaModule {
    public static ReactApplicationContext reactApplicationContext;
    private IWoyouService woyouService;
    private BitmapUtils bitMapUtils;
    private PrinterReceiver receiver=new PrinterReceiver();

    // 缺纸异常
    public final static String OUT_OF_PAPER_ACTION = "woyou.aidlservice.jiuv5.OUT_OF_PAPER_ACTION";
    // 打印错误
    public final static String ERROR_ACTION = "woyou.aidlservice.jiuv5.ERROR_ACTION";
    // 可以打印
    public final static String NORMAL_ACTION = "woyou.aidlservice.jiuv5.NORMAL_ACTION";
    // 开盖子
    public final static String COVER_OPEN_ACTION = "woyou.aidlservice.jiuv5.COVER_OPEN_ACTION";
    // 关盖子异常
    public final static String COVER_ERROR_ACTION = "woyou.aidlservice.jiuv5.COVER_ERROR_ACTION";
    // 切刀异常1－卡切刀
    public final static String KNIFE_ERROR_1_ACTION = "woyou.aidlservice.jiuv5.KNIFE_ERROR_ACTION_1";
    // 切刀异常2－切刀修复
    public final static String KNIFE_ERROR_2_ACTION = "woyou.aidlservice.jiuv5.KNIFE_ERROR_ACTION_2";
    // 打印头过热异常
    public final static String OVER_HEATING_ACITON = "woyou.aidlservice.jiuv5.OVER_HEATING_ACITON";
    // 打印机固件开始升级
    public final static String FIRMWARE_UPDATING_ACITON = "woyou.aidlservice.jiuv5.FIRMWARE_UPDATING_ACITON";

    // CUSTOM
    public static int NoSunmiPrinter = 0x00000000;
    public static int CheckSunmiPrinter = 0x00000001;
    public static int FoundSunmiPrinter = 0x00000002;
    public static int LostSunmiPrinter = 0x00000003;

    /**
     *  sunmiPrinter means checking the printer connection status
     */
    public int sunmiPrinter = CheckSunmiPrinter;
    /**
     *  SunmiPrinterService for API
     */
    private SunmiPrinterService sunmiPrinterService;

    private InnerPrinterCallback innerPrinterCallback = new InnerPrinterCallback() {
        @Override
        protected void onConnected(SunmiPrinterService service) {
            sunmiPrinterService = service;
            checkSunmiPrinterService(service);

            showPrinterStatus();
            //test();
            Log.i(TAG, "SunmiPrinter is Ready");
        }

        @Override
        protected void onDisconnected() {
            sunmiPrinterService = null;
            sunmiPrinter = LostSunmiPrinter;
            Log.i(TAG, "SunmiPrinter is Not Ready");
        }
    };

    /**
     * init sunmi print service
     */
    public void initSunmiPrinterService(Context context){
        try {
            boolean ret =  InnerPrinterManager.getInstance().bindService(context,
                    innerPrinterCallback);
            if(!ret){
                sunmiPrinter = NoSunmiPrinter;
            }
        } catch (InnerPrinterException e) {
            e.printStackTrace();
        }
    }

    /**
     *  deInit sunmi print service
     */
    public void deInitSunmiPrinterService(Context context){
        try {
            if(sunmiPrinterService != null){
                InnerPrinterManager.getInstance().unBindService(context, innerPrinterCallback);
                sunmiPrinterService = null;
                sunmiPrinter = LostSunmiPrinter;
            }
        } catch (InnerPrinterException e) {
            e.printStackTrace();
        }
    }

    /**
     * Check the printer connection,
     * like some devices do not have a printer but need to be connected to the cash drawer through a print service
     */
    private void checkSunmiPrinterService(SunmiPrinterService service){
        boolean ret = false;
        try {
            ret = InnerPrinterManager.getInstance().hasPrinter(service);
        } catch (InnerPrinterException e) {
            e.printStackTrace();
        }
        sunmiPrinter = ret?FoundSunmiPrinter:NoSunmiPrinter;
    }

    /**
     *  Some conditions can cause interface calls to fail
     *  For example: the version is too low、device does not support
     *  You can see {@link ExceptionConst}
     *  So you have to handle these exceptions
     */
    private void handleRemoteException(RemoteException e){
        //TODO process when get one exception
    }
    // END CUSTOM

    private ServiceConnection connService = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "Service disconnected: " + name);
            woyouService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "Service connected: " + name);
            woyouService = IWoyouService.Stub.asInterface(service);
        }
    };

    private static final String TAG = "SunmiInnerPrinterModule";

    public SunmiInnerPrinterModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactApplicationContext = reactContext;
		Intent intent = new Intent();
        intent.setPackage("woyou.aidlservice.jiuiv5");
        intent.setAction("woyou.aidlservice.jiuiv5.IWoyouService");
        reactContext.startService(intent);
        reactContext.bindService(intent, connService, Context.BIND_AUTO_CREATE);
        bitMapUtils = new BitmapUtils(reactContext);
        IntentFilter mFilter = new IntentFilter();
        mFilter.addAction(OUT_OF_PAPER_ACTION);
        mFilter.addAction(ERROR_ACTION);
        mFilter.addAction(NORMAL_ACTION);
        mFilter.addAction(COVER_OPEN_ACTION);
        mFilter.addAction(COVER_ERROR_ACTION);
        mFilter.addAction(KNIFE_ERROR_1_ACTION);
        mFilter.addAction(KNIFE_ERROR_2_ACTION);
        mFilter.addAction(OVER_HEATING_ACITON);
        mFilter.addAction(FIRMWARE_UPDATING_ACITON);
        getReactApplicationContext().registerReceiver(receiver, mFilter);

        initSunmiPrinterService(reactContext);
        //Log.d("WoyouConsts.ENABLE_BOLD", WoyouConsts.ENABLE_BOLD + "");
        //Log.d("WoyouConsts.SET_LINE_SPACING", WoyouConsts.SET_LINE_SPACING + "");
        //Log.d("WoyouConsts.ENABLE", WoyouConsts.ENABLE + "");
        //Log.d("WoyouConsts.DISABLE", WoyouConsts.DISABLE + "");

        Log.d("PrinterReceiver", "------------ init ");
    }

    @Override
    public String getName() {
        return "SunmiInnerPrinter";
    }


    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        final Map<String, Object> constantsChildren = new HashMap<>();

        constantsChildren.put("OUT_OF_PAPER_ACTION", OUT_OF_PAPER_ACTION);
        constantsChildren.put("ERROR_ACTION", ERROR_ACTION);
        constantsChildren.put("NORMAL_ACTION", NORMAL_ACTION);
        constantsChildren.put("COVER_OPEN_ACTION", COVER_OPEN_ACTION);
        constantsChildren.put("COVER_ERROR_ACTION", COVER_ERROR_ACTION);
        constantsChildren.put("KNIFE_ERROR_1_ACTION", KNIFE_ERROR_1_ACTION);
        constantsChildren.put("KNIFE_ERROR_2_ACTION", KNIFE_ERROR_2_ACTION);
        constantsChildren.put("OVER_HEATING_ACITON", OVER_HEATING_ACITON);
        constantsChildren.put("FIRMWARE_UPDATING_ACITON", FIRMWARE_UPDATING_ACITON);

        constants.put("Constants", constantsChildren);

        constants.put("hasPrinter", hasPrinter());

        try {
            constants.put("printerVersion", getPrinterVersion());
        } catch (Exception e) {
            // Log and ignore for it is not the madatory constants.
            Log.i(TAG, "ERROR: " + e.getMessage());
        }
        try {
            constants.put("printerSerialNo", getPrinterSerialNo());
        } catch (Exception e) {
            // Log and ignore for it is not the madatory constants.
            Log.i(TAG, "ERROR: " + e.getMessage());
        }
        try {
            constants.put("printerModal", getPrinterModal());
        } catch (Exception e) {
            // Log and ignore for it is not the madatory constants.
            Log.i(TAG, "ERROR: " + e.getMessage());
        }

        return constants;
    }


    /**
     * 初始化打印机，重置打印机的逻辑程序，但不清空缓存区数据，因此
     * 未完成的打印作业将在重置后继续
     *
     * @return
     */
    @ReactMethod
    public void printerInit(final Promise p) {
        final IWoyouService printerService = woyouService;
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    printerService.printerInit(new ICallback.Stub() {
                        @Override
                        public void onRunResult(boolean isSuccess) {
                            if (isSuccess) {
                                p.resolve(null);
                            } else {
                                p.reject("0", isSuccess + "");
                            }
                        }

                        @Override
                        public void onReturnString(String result) {
                            p.resolve(result);
                        }

                        @Override
                        public void onRaiseException(int code, String msg) {
                            p.reject("" + code, msg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                    p.reject("" + 0, e.getMessage());
                }
            }
        });
    }

    /**
     * 打印机自检，打印机会打印自检页
     *
     * @param callback 回调
     */
    @ReactMethod
    public void printerSelfChecking(final Promise p) {
        final IWoyouService printerService = woyouService;
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    printerService.printerSelfChecking(new ICallback.Stub() {
                        @Override
                        public void onRunResult(boolean isSuccess) {
                            if (isSuccess) {
                                p.resolve(null);
                            } else {
                                p.reject("0", isSuccess + "");
                            }
                        }

                        @Override
                        public void onReturnString(String result) {
                            p.resolve(result);
                        }

                        @Override
                        public void onRaiseException(int code, String msg) {
                            p.reject("" + code, msg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                    p.reject("" + 0, e.getMessage());
                }
            }
        });
    }

    /**
     * 获取打印机板序列号
     */
    @ReactMethod
    public void getPrinterSerialNo(final Promise p) {
        try {
            p.resolve(getPrinterSerialNo());
        } catch (Exception e) {
            Log.i(TAG, "ERROR: " + e.getMessage());
            p.reject("" + 0, e.getMessage());
        }
    }

    private String getPrinterSerialNo() throws Exception {
        final IWoyouService printerService = woyouService;
        return printerService.getPrinterSerialNo();
    }

    /**
     * 获取打印机固件版本号
     */
    @ReactMethod
    public void getPrinterVersion(final Promise p) {
        try {
            p.resolve(getPrinterVersion());
        } catch (Exception e) {
            Log.i(TAG, "ERROR: " + e.getMessage());
            p.reject("" + 0, e.getMessage());
        }
    }

    private String getPrinterVersion() throws Exception {
        final IWoyouService printerService = woyouService;
        return printerService.getPrinterVersion();
    }

    /**
     * 获取打印机型号
     */
    @ReactMethod
    public void getPrinterModal(final Promise p) {
        try {
            p.resolve(getPrinterModal());
        } catch (Exception e) {
            Log.i(TAG, "ERROR: " + e.getMessage());
            p.reject("" + 0, e.getMessage());
        }
    }

    private String getPrinterModal() throws Exception {
        //Caution: This method is not fully test -- Januslo 2018-08-11
        final IWoyouService printerService = woyouService;
        return printerService.getPrinterModal();
    }

    @ReactMethod
    public void hasPrinter(final Promise p) {
        try {
            p.resolve(hasPrinter());
        } catch (Exception e) {
            Log.i(TAG, "ERROR: " + e.getMessage());
            p.reject("" + 0, e.getMessage());
        }
    }

    /**
     * 是否存在打印机服务
     * return {boolean}
     */
    private boolean hasPrinter() {
        final IWoyouService printerService = woyouService;
        final boolean hasPrinterService = printerService != null;
        return hasPrinterService;
    }

    /**
     * 获取打印头打印长度
     */
    @ReactMethod
    public void getPrintedLength(final Promise p) {
        final IWoyouService printerService = woyouService;
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    printerService.getPrintedLength(new ICallback.Stub() {
                        @Override
                        public void onRunResult(boolean isSuccess) {
                            if (isSuccess) {
                                p.resolve(null);
                            } else {
                                p.reject("0", isSuccess + "");
                            }
                        }

                        @Override
                        public void onReturnString(String result) {
                            p.resolve(result);
                        }

                        @Override
                        public void onRaiseException(int code, String msg) {
                            p.reject("" + code, msg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                    p.reject("" + 0, e.getMessage());
                }
            }
        });
    }

    /**
     * 打印机走纸(强制换行，结束之前的打印内容后走纸n行)
     *
     * @param n:       走纸行数
     * @param callback 结果回调
     * @return
     */
    @ReactMethod
    public void lineWrap(int n, final Promise p) {
        final IWoyouService ss = woyouService;
        final int count = n;
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    ss.lineWrap(count, new ICallback.Stub() {
                        @Override
                        public void onRunResult(boolean isSuccess) {
                            if (isSuccess) {
                                p.resolve(null);
                            } else {
                                p.reject("0", isSuccess + "");
                            }
                        }

                        @Override
                        public void onReturnString(String result) {
                            p.resolve(result);
                        }

                        @Override
                        public void onRaiseException(int code, String msg) {
                            p.reject("" + code, msg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                    p.reject("" + 0, e.getMessage());
                }
            }
        });
    }

    /**
     * 使用原始指令打印
     *
     * @param data     指令
     * @param callback 结果回调
     */
    @ReactMethod
    public void sendRAWData(String base64EncriptedData, final Promise p) {
        final IWoyouService ss = woyouService;
        final byte[] d = Base64.decode(base64EncriptedData, Base64.DEFAULT);
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    ss.sendRAWData(d, new ICallback.Stub() {
                        @Override
                        public void onRunResult(boolean isSuccess) {
                            if (isSuccess) {
                                p.resolve(null);
                            } else {
                                p.reject("0", isSuccess + "");
                            }
                        }

                        @Override
                        public void onReturnString(String result) {
                            p.resolve(result);
                        }

                        @Override
                        public void onRaiseException(int code, String msg) {
                            p.reject("" + code, msg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                    p.reject("" + 0, e.getMessage());
                }
            }
        });
    }

    /**
     * 设置对齐模式，对之后打印有影响，除非初始化
     *
     * @param alignment: 对齐方式 0--居左 , 1--居中, 2--居右
     * @param callback   结果回调
     */
    @ReactMethod
    public void setAlignment(int alignment, final Promise p) {
        final IWoyouService ss = woyouService;
        final int align = alignment;
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    ss.setAlignment(align, new ICallback.Stub() {
                        @Override
                        public void onRunResult(boolean isSuccess) {
                            if (isSuccess) {
                                p.resolve(null);
                            } else {
                                p.reject("0", isSuccess + "");
                            }
                        }

                        @Override
                        public void onReturnString(String result) {
                            p.resolve(result);
                        }

                        @Override
                        public void onRaiseException(int code, String msg) {
                            p.reject("" + code, msg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                    p.reject("" + 0, e.getMessage());
                }
            }
        });
    }

    /**
     * 设置打印字体, 对之后打印有影响，除非初始化
     * (目前只支持一种字体"gh"，gh是一种等宽中文字体，之后会提供更多字体选择)
     *
     * @param typeface: 字体名称
     */
    @ReactMethod
    public void setFontName(String typeface, final Promise p) {
        final IWoyouService ss = woyouService;
        final String tf = typeface;
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    ss.setFontName(tf, new ICallback.Stub() {
                        @Override
                        public void onRunResult(boolean isSuccess) {
                            if (isSuccess) {
                                p.resolve(null);
                            } else {
                                p.reject("0", isSuccess + "");
                            }
                        }

                        @Override
                        public void onReturnString(String result) {
                            p.resolve(result);
                        }

                        @Override
                        public void onRaiseException(int code, String msg) {
                            p.reject("" + code, msg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                    p.reject("" + 0, e.getMessage());
                }
            }
        });
    }

    /**
     * 设置字体大小, 对之后打印有影响，除非初始化
     * 注意：字体大小是超出标准国际指令的打印方式，
     * 调整字体大小会影响字符宽度，每行字符数量也会随之改变，
     * 因此按等宽字体形成的排版可能会错乱
     *
     * @param fontsize: 字体大小
     */
    @ReactMethod
    public void setFontSize(float fontsize, final Promise p) {
        final IWoyouService ss = woyouService;
        final float fs = fontsize;
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    ss.setFontSize(fs, new ICallback.Stub() {
                        @Override
                        public void onRunResult(boolean isSuccess) {
                            if (isSuccess) {
                                p.resolve(null);
                            } else {
                                p.reject("0", isSuccess + "");
                            }
                        }

                        @Override
                        public void onReturnString(String result) {
                            p.resolve(result);
                        }

                        @Override
                        public void onRaiseException(int code, String msg) {
                            p.reject("" + code, msg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                    p.reject("" + 0, e.getMessage());
                }
            }
        });
    }


    /**
     * 打印指定字体的文本，字体设置只对本次有效
     *
     * @param text:     要打印文字
     * @param typeface: 字体名称（目前只支持"gh"字体）
     * @param fontsize: 字体大小
     */
    @ReactMethod
    public void printTextWithFont(String text, String typeface, float fontsize, final Promise p) {
        final IWoyouService ss = woyouService;
        final String txt = text;
        final String tf = typeface;
        final float fs = fontsize;
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    ss.printTextWithFont(txt, tf, fs, new ICallback.Stub() {
                        @Override
                        public void onRunResult(boolean isSuccess) {
                            if (isSuccess) {
                                p.resolve(null);
                            } else {
                                p.reject("0", isSuccess + "");
                            }
                        }

                        @Override
                        public void onReturnString(String result) {
                            p.resolve(result);
                        }

                        @Override
                        public void onRaiseException(int code, String msg) {
                            p.reject("" + code, msg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                    p.reject("" + 0, e.getMessage());
                }
            }
        });
    }

    /**
     * 打印表格的一行，可以指定列宽、对齐方式
     *
     * @param colsTextArr  各列文本字符串数组
     * @param colsWidthArr 各列宽度数组(以英文字符计算, 每个中文字符占两个英文字符, 每个宽度大于0)
     * @param colsAlign    各列对齐方式(0居左, 1居中, 2居右)
     *                     备注: 三个参数的数组长度应该一致, 如果colsText[i]的宽度大于colsWidth[i], 则文本换行
     */
    @ReactMethod
    public void printColumnsText(ReadableArray colsTextArr, ReadableArray colsWidthArr, ReadableArray colsAlign, final Promise p) {
        final IWoyouService ss = woyouService;
        final String[] clst = new String[colsTextArr.size()];
        for (int i = 0; i < colsTextArr.size(); i++) {
            clst[i] = colsTextArr.getString(i);
        }
        final int[] clsw = new int[colsWidthArr.size()];
        for (int i = 0; i < colsWidthArr.size(); i++) {
            clsw[i] = colsWidthArr.getInt(i);
        }
        final int[] clsa = new int[colsAlign.size()];
        for (int i = 0; i < colsAlign.size(); i++) {
            clsa[i] = colsAlign.getInt(i);
        }
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    ss.printColumnsText(clst, clsw, clsa, new ICallback.Stub() {
                        @Override
                        public void onRunResult(boolean isSuccess) {
                            if (isSuccess) {
                                p.resolve(null);
                            } else {
                                p.reject("0", isSuccess + "");
                            }
                        }

                        @Override
                        public void onReturnString(String result) {
                            p.resolve(result);
                        }

                        @Override
                        public void onRaiseException(int code, String msg) {
                            p.reject("" + code, msg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                    p.reject("" + 0, e.getMessage());
                }
            }
        });
    }


    /**
     * 打印图片
     *
     * @param bitmap: 图片bitmap对象(最大宽度384像素，超过无法打印并且回调callback异常函数)
     */
    @ReactMethod
    public void printBitmap(String data, int width, int height, final Promise p) {
        try {
            final IWoyouService ss = woyouService;
            byte[] decoded = Base64.decode(data, Base64.DEFAULT);
            final Bitmap bitMap = bitMapUtils.decodeBitmap(decoded, width, height);
            ThreadPoolManager.getInstance().executeTask(new Runnable() {
                @Override
                public void run() {
                    try {
                        ss.printBitmap(bitMap, new ICallback.Stub() {
                            @Override
                            public void onRunResult(boolean isSuccess) {
                                if (isSuccess) {
                                    p.resolve(null);
                                } else {
                                    p.reject("0", isSuccess + "");
                                }
                            }

                            @Override
                            public void onReturnString(String result) {
                                p.resolve(result);
                            }

                            @Override
                            public void onRaiseException(int code, String msg) {
                                p.reject("" + code, msg);
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.i(TAG, "ERROR: " + e.getMessage());
                        p.reject("" + 0, e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "ERROR: " + e.getMessage());
        }
    }

    /**
     * 打印一维条码
     *
     * @param data:         条码数据
     * @param symbology:    条码类型
     *                      0 -- UPC-A，
     *                      1 -- UPC-E，
     *                      2 -- JAN13(EAN13)，
     *                      3 -- JAN8(EAN8)，
     *                      4 -- CODE39，
     *                      5 -- ITF，
     *                      6 -- CODABAR，
     *                      7 -- CODE93，
     *                      8 -- CODE128
     * @param height:       条码高度, 取值1到255, 默认162
     * @param width:        条码宽度, 取值2至6, 默认2
     * @param textposition: 文字位置 0--不打印文字, 1--文字在条码上方, 2--文字在条码下方, 3--条码上下方均打印
     */
    @ReactMethod
    public void printBarCode(String data, int symbology, int height, int width, int textposition, final Promise p) {
        final IWoyouService ss = woyouService;
        Log.i(TAG, "come: ss:" + ss);
        final String d = data;
        final int s = symbology;
        final int h = height;
        final int w = width;
        final int tp = textposition;

        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    ss.printBarCode(d, s, h, w, tp, new ICallback.Stub() {
                        @Override
                        public void onRunResult(boolean isSuccess) {
                            if (isSuccess) {
                                p.resolve(null);
                            } else {
                                p.reject("0", isSuccess + "");
                            }
                        }

                        @Override
                        public void onReturnString(String result) {
                            p.resolve(result);
                        }

                        @Override
                        public void onRaiseException(int code, String msg) {
                            p.reject("" + code, msg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                    p.reject("" + 0, e.getMessage());
                }
            }
        });
    }

    /**
     * 打印二维条码
     *
     * @param data:       二维码数据
     * @param modulesize: 二维码块大小(单位:点, 取值 1 至 16 )
     * @param errorlevel: 二维码纠错等级(0 至 3)，
     *                    0 -- 纠错级别L ( 7%)，
     *                    1 -- 纠错级别M (15%)，
     *                    2 -- 纠错级别Q (25%)，
     *                    3 -- 纠错级别H (30%)
     */
    @ReactMethod
    public void printQRCode(String data, int modulesize, int errorlevel, final Promise p) {
        final IWoyouService ss = woyouService;
        Log.i(TAG, "come: ss:" + ss);
        final String d = data;
        final int size = modulesize;
        final int level = errorlevel;
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    ss.printQRCode(d, size, level, new ICallback.Stub() {
                        @Override
                        public void onRunResult(boolean isSuccess) {
                            if (isSuccess) {
                                p.resolve(null);
                            } else {
                                p.reject("0", isSuccess + "");
                            }
                        }

                        @Override
                        public void onReturnString(String result) {
                            p.resolve(result);
                        }

                        @Override
                        public void onRaiseException(int code, String msg) {
                            p.reject("" + code, msg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                    p.reject("" + 0, e.getMessage());
                }
            }
        });
    }

    /**
     * 打印文字，文字宽度满一行自动换行排版，不满一整行不打印除非强制换行
     * 文字按矢量文字宽度原样输出，即每个字符不等宽
     *
     * @param text: 要打印的文字字符串
     */
    @ReactMethod
    public void printOriginalText(String text, final Promise p) {
        final IWoyouService ss = woyouService;
        Log.i(TAG, "come: " + text + " ss:" + ss);
        final String txt = text;
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    ss.printOriginalText(txt, new ICallback.Stub() {
                        @Override
                        public void onRunResult(boolean isSuccess) {
                            if (isSuccess) {
                                p.resolve(null);
                            } else {
                                p.reject("0", isSuccess + "");
                            }
                        }

                        @Override
                        public void onReturnString(String result) {
                            p.resolve(result);
                        }

                        @Override
                        public void onRaiseException(int code, String msg) {
                            p.reject("" + code, msg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                    p.reject("" + 0, e.getMessage());
                }
            }
        });
    }

    /**
     * 打印缓冲区内容
     */
    @ReactMethod
    public void commitPrinterBuffer() {
        final IWoyouService ss = woyouService;
        Log.i(TAG, "come: commit buffter ss:" + ss);
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    ss.commitPrinterBuffer();
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 进入缓冲模式，所有打印调用将缓存，调用commitPrinterBuffe()后打印
     *
     * @param clean: 是否清除缓冲区内容
     */
    @ReactMethod
    public void enterPrinterBuffer(boolean clean) {
        final IWoyouService ss = woyouService;
        Log.i(TAG, "come: " + clean + " ss:" + ss);
        final boolean c = clean;
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    ss.enterPrinterBuffer(c);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 退出缓冲模式
     *
     * @param commit: 是否打印出缓冲区内容
     */
    @ReactMethod
    public void exitPrinterBuffer(boolean commit) {
        final IWoyouService ss = woyouService;
        Log.i(TAG, "come: " + commit + " ss:" + ss);
        final boolean com = commit;
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    ss.exitPrinterBuffer(com);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                }
            }
        });
    }


    @ReactMethod
    public void printString(String message, final Promise p) {
        final IWoyouService ss = woyouService;
        Log.i(TAG, "come: " + message + " ss:" + ss);
        final String msgs = message;
        ThreadPoolManager.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                try {
                    ss.printText(msgs, new ICallback.Stub() {
                        @Override
                        public void onRunResult(boolean isSuccess) {
                            if (isSuccess) {
                                p.resolve(null);
                            } else {
                                p.reject("0", isSuccess + "");
                            }
                        }

                        @Override
                        public void onReturnString(String result) {
                            p.resolve(result);
                        }

                        @Override
                        public void onRaiseException(int code, String msg) {
                            p.reject("" + code, msg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "ERROR: " + e.getMessage());
                    p.reject("" + 0, e.getMessage());
                }
            }
        });
    }

    /**
     * Used to report the real-time query status of the printer, which can be used before each
     * printing
     */
    public void showPrinterStatus(){
        if(sunmiPrinterService == null){
			Log.i(TAG, "Printer Service disconnection processing");
            //TODO Service disconnection processing
            return;
        }
        String result = "Interface is too low to implement interface";
        try {
            int res = sunmiPrinterService.updatePrinterState();
            switch (res){
                case 1:
                    result = "printer is running";
                    break;
                case 2:
                    result = "printer found but still initializing";
                    break;
                case 3:
                    result = "printer hardware interface is abnormal and needs to be reprinted";
                    break;
                case 4:
                    result = "printer is out of paper";
                    break;
                case 5:
                    result = "printer is overheating";
                    break;
                case 6:
                    result = "printer's cover is not closed";
                    break;
                case 7:
                    result = "printer's cutter is abnormal";
                    break;
                case 8:
                    result = "printer's cutter is normal";
                    break;
                case 9:
                    result = "not found black mark paper";
                    break;
                case 505:
                    result = "printer does not exist";
                    break;
                default:
                    break;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "Printer Status : " + result);
    }

	public void test()
    {
        Log.i(TAG, "Begin test print");
        //Toast.makeText(context, "HELLO", Toast.LENGTH_SHORT).show();
		if(sunmiPrinterService == null){
			//TODO Service disconnection processing
			return;
		}

		try {
			sunmiPrinterService.enterPrinterBuffer(true);

			int paper = sunmiPrinterService.getPrinterPaper();
			sunmiPrinterService.printerInit(null);
			sunmiPrinterService.setAlignment(1, null); // clear
			sunmiPrinterService.printText("测试样张\n", null); // clear
			//Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.sunmi);
			//sunmiPrinterService.printBitmap(bitmap, null);
			sunmiPrinterService.lineWrap(1, null); // clear
			sunmiPrinterService.setAlignment(0, null); // clear
			try {
			    sunmiPrinterService.setPrinterStyle(WoyouConsts.SET_LINE_SPACING, 0);
			} catch (RemoteException e) {
			    sunmiPrinterService.sendRAWData(new byte[]{0x1B, 0x33, 0x00}, null);
			}
			sunmiPrinterService.printTextWithFont("说明：这是一个自定义的小票样式例子,开发者可以仿照此进行自己的构建\n",
			        null, 12, null); // clear
			if(paper == 1){
			    sunmiPrinterService.printText("--------------------------------\n", null); // clear
			}else{
			    sunmiPrinterService.printText("------------------------------------------------\n", null); // clear
			}
			try {
			    sunmiPrinterService.setPrinterStyle(WoyouConsts.ENABLE_BOLD, WoyouConsts.ENABLE); // clear
			} catch (RemoteException e) {
			    //sunmiPrinterService.sendRAWData(ESCUtil.boldOn(), null);
			}
			String txts[] = new String[]{"商品", "价格"};
			int width[] = new int[]{1, 1};
			int align[] = new int[]{0, 2};
			sunmiPrinterService.printColumnsString(txts, width, align, null);
			try {
			    sunmiPrinterService.setPrinterStyle(WoyouConsts.ENABLE_BOLD, WoyouConsts.DISABLE); // clear
			} catch (RemoteException e) {
			    //sunmiPrinterService.sendRAWData(ESCUtil.boldOff(), null);
			}
			if(paper == 1){
			    sunmiPrinterService.printText("--------------------------------\n", null); // clear
			}else{
			    sunmiPrinterService.printText("------------------------------------------------\n", null); // clear
			}
			txts[0] = "ต้นสนวุ้นเส้น AAb ProVision LED DigitalTV 32 นิ้ว รุ่น LT32G33 (รับประกัน 1 ปี)";
			txts[1] = "10,000.00";
			sunmiPrinterService.printColumnsString(txts, width, align, null);
			txts[0] = "可乐";
			txts[1] = "10¥";
			sunmiPrinterService.printColumnsString(txts, width, align, null);
			txts[0] = "薯条";
			txts[1] = "11¥";
			sunmiPrinterService.printColumnsString(txts, width, align, null);
			txts[0] = "炸鸡";
			txts[1] = "11¥";
			sunmiPrinterService.printColumnsString(txts, width, align, null);
			txts[0] = "圣代";
			txts[1] = "10¥";
			sunmiPrinterService.printColumnsString(txts, width, align, null);
			if(paper == 1){
			    sunmiPrinterService.printText("--------------------------------\n", null); // clear
			}else{
			    sunmiPrinterService.printText("------------------------------------------------\n", // clear
			            null);
			}
			sunmiPrinterService.printTextWithFont("总计:          59¥\b", null, 40, null); // clear
			sunmiPrinterService.setAlignment(1, null); // clear
			//sunmiPrinterService.printQRCode("谢谢惠顾", 10, 0, null);
			sunmiPrinterService.setFontSize(36, null); // clear
			sunmiPrinterService.printText("谢谢惠顾", null); // clear
			//sunmiPrinterService.autoOutPaper(null);
			sunmiPrinterService.cutPaper(null); // clear

			sunmiPrinterService.exitPrinterBufferWithCallback(true, null);

			Log.i(TAG, "End test print");
		} catch (RemoteException e) {
			e.printStackTrace();
		}
    }

	@ReactMethod
	public void setAlignment2(int alignment, final Promise p) {
		Log.i(TAG, "ERROR: setAlignment2");
		try {
			final int align = alignment;
			sunmiPrinterService.setAlignment(align, null);
		} catch (RemoteException e) {
            e.printStackTrace();
			Log.i(TAG, "ERROR: " + e.getMessage());
        }
	}

	@ReactMethod
	public void printText(String text, final Promise p) {
		Log.i(TAG, "ERROR: printText");
		try {
			sunmiPrinterService.printText(text, null);
		} catch (RemoteException e) {
            e.printStackTrace();
			Log.i(TAG, "ERROR: " + e.getMessage());
        }
	}

	@ReactMethod
	public void lineWrap2(int x, final Promise p) {
		Log.i(TAG, "ERROR: lineWrap2");
		try {
			sunmiPrinterService.lineWrap(x, null);
		} catch (RemoteException e) {
            e.printStackTrace();
			Log.i(TAG, "ERROR: " + e.getMessage());
        }
	}

	@ReactMethod
	public void printTextWithFont2(String text, String typeface, float fontsize, final Promise p) {
		Log.i(TAG, "ERROR: printTextWithFont2");
		try {
			//final String txt = text;
			//final String tf = typeface;
			//final float fs = fontsize;
			sunmiPrinterService.printTextWithFont(
				text,
				typeface != "" ? typeface : null,
				fontsize,
				null
			);
		} catch (RemoteException e) {
            e.printStackTrace();
			Log.i(TAG, "ERROR: " + e.getMessage());
        }
	}

	@ReactMethod
	public void setFontName2(String text) {
		Log.i(TAG, "ERROR: setFontName2");
		try {
			sunmiPrinterService.setFontName(
				text,
				null
			);
		} catch (RemoteException e) {
            e.printStackTrace();
			Log.i(TAG, "ERROR: " + e.getMessage());
        }
	}

	@ReactMethod
    public void setPrinterStyle(int x1, int x2, final Promise p) {
        Log.i(TAG, "ERROR: setPrinterStyle");
        try {
            sunmiPrinterService.setPrinterStyle(x1, x2);
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.i(TAG, "ERROR: " + e.getMessage());
        }
    }

	@ReactMethod
	public void setFontSize2(int x, final Promise p) {
		Log.i(TAG, "ERROR: setFontSize2");
		try {
			sunmiPrinterService.setFontSize(x, null);
		} catch (RemoteException e) {
            e.printStackTrace();
			Log.i(TAG, "ERROR: " + e.getMessage());
        }
	}

	@ReactMethod
	public void cutPaper(final Promise p) {
		Log.i(TAG, "ERROR: cutPaper");
		try {
			sunmiPrinterService.cutPaper(null);
		} catch (RemoteException e) {
            e.printStackTrace();
            Log.i(TAG, "ERROR: " + e.getMessage());
        }
	}

	@ReactMethod
	public void printerInit2(final Promise p) {
		Log.i(TAG, "ERROR: printerInit2");
		try {
			sunmiPrinterService.printerInit(null);
		} catch (RemoteException e) {
            e.printStackTrace();
            Log.i(TAG, "ERROR: " + e.getMessage());
        }
	}

	@ReactMethod
	public void enterPrinterBuffer2(boolean clean) {
		Log.i(TAG, "ERROR: enterPrinterBuffer2");
		try {
			sunmiPrinterService.enterPrinterBuffer(clean);
		} catch (RemoteException e) {
            e.printStackTrace();
            Log.i(TAG, "ERROR: " + e.getMessage());
        }
	}

	@ReactMethod
	public void exitPrinterBufferWithCallback(boolean clean) {
		//InnerResultCallbcak callback = null;
		Log.i(TAG, "ERROR: exitPrinterBufferWithCallback");
		try {
			sunmiPrinterService.exitPrinterBufferWithCallback(clean, null);
		} catch (RemoteException e) {
            e.printStackTrace();
            Log.i(TAG, "ERROR: " + e.getMessage());
        }
	}

	@ReactMethod
    public void printColumnsString(ReadableArray colsTextArr, ReadableArray colsWidthArr, ReadableArray colsAlign, final Promise p) {
        Log.i(TAG, "ERROR: printColumnsString");
        try {
	        final String[] txts = new String[colsTextArr.size()];
	        for (int i = 0; i < colsTextArr.size(); i++) {
	            txts[i] = colsTextArr.getString(i);
	        }
	        final int[] width = new int[colsWidthArr.size()];
	        for (int i = 0; i < colsWidthArr.size(); i++) {
	            width[i] = colsWidthArr.getInt(i);
	        }
	        final int[] align = new int[colsAlign.size()];
	        for (int i = 0; i < colsAlign.size(); i++) {
	            align[i] = colsAlign.getInt(i);
	        }
	        sunmiPrinterService.printColumnsString(txts, width, align, null);
        } catch (RemoteException e) {
            e.printStackTrace();
			Log.i(TAG, "ERROR: " + e.getMessage());
        }
    }

	@ReactMethod
	public void sendRAWData2(String base64EncriptedData, final Promise p) {
		Log.i(TAG, "ERROR: sendRAWData2");
		try {
			final byte[] d = Base64.decode(base64EncriptedData, Base64.DEFAULT);
			sunmiPrinterService.sendRAWData(d, null);
		} catch (RemoteException e) {
            e.printStackTrace();
            Log.i(TAG, "ERROR: " + e.getMessage());
        }
	}

}
