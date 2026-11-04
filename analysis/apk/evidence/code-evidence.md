# Static code evidence

The excerpts have been copied unchanged from the JADX 1.5.6 output. Each block includes the original path and range. They are not execution tests.

## E001 app_config

Server, OEM, IDs and Wibox capabilities.

Source: `com/quvii/ubell/publico/common/AppConfig.java:13-40`

```java
    public static final int APP_DA_DEFAULT_CHANNELS = 1;
    public static final String APP_DA_DEFAULT_DEVICE_NAME = "Device1";
    public static final String APP_DA_DEFAULT_USER_NAME = "adminapp";
    public static final boolean APP_DEVICE_LIST_SHOW_SETTING = false;
    public static final boolean APP_DEVICE_LIST_SHOW_SHARE = false;
    public static final String APP_FONT_NAME = "fonts/alger.ttf";
    public static final int APP_ID = 2021;
    public static final boolean APP_IS_SET_CUSTOM_FONT = false;
    public static final boolean APP_IS_TITLE_DARK_FONT = false;
    public static final int APP_SHOW_VERSION = 2;
    public static final String APP_UPGRADE_ADDRESS = "matainde.qvcloud.net";
    public static final int APP_UPGRADE_PORT = 1443;
    public static final boolean App_CONFIG_IS_SHOW_PREVIEW_TIME = false;
    public static final int App_CONFIG_PREVIEW_TIME_DEFAULT = 90;
    public static final int App_CONFIG_PREVIEW_TIME_MAX = 300;
    public static final int App_CONFIG_PREVIEW_TIME_MIN = 60;
    public static final boolean App_CONFIG_SUPPORT_SHARE = true;
    public static final int BASE_AUTH_TIMEOUT = 35;
    public static final int CLIENT_TYPE = 3;
    public static final boolean IS_OPEN_PUSH_LOG = true;
    public static final String OEM_ID = "G0021";
    public static final int PUSH_MODE = 1;
    public static final String SERVER_ADDRESS = "fermax.qvcloud.net";
    public static final int SERVER_PORT = 443;
    public static final String SHARE_OEM_ID = "G0021";
    public static boolean debug = false;
    public static final String[] APP_DEVICE_AP_NAME_START = {"IDS", "ODS"};
    public static final String[] APP_IOT_DEVICE_AP_NAME_START = {"IOT"};
```

## E002 app_release_debug

HTTP/debug flags and startup of database, push and updater.

Source: `com/quvii/ubell/publico/App.java:185-220`

```java
    @Override // com.quvii.qvlib.base.QvBaseApp, android.app.Application
    public void onCreate() {
        super.onCreate();
        SDKConfig.DEBUG_HTTP = true;
        SDKConfig.DEV_MODE = true;
        AppVariates.debugMode = SpUtil.getInstance().isAppDebugMode();
        QvCore.getInstance().setLogShown(true);
        initQvLib();
        SDKConfig.isAccountLogin = false;
        QvWifiUtil.getInstance().init(this);
        if (SpUtil.getInstance().getDBVersion() != 1) {
            String absolutePath = Build.VERSION.SDK_INT >= 24 ? getDataDir().getAbsolutePath() : getFilesDir().getParent();
            StringBuilder sb = new StringBuilder();
            sb.append(absolutePath);
            String str = File.separator;
            sb.append(str);
            sb.append("databases");
            sb.append(str);
            sb.append(AppDatabase.NAME);
            sb.append(".db");
            boolean zExists = new File(sb.toString()).exists();
            com.quvii.qvlib.util.LogUtil.i("file exists: " + zExists);
            if (!zExists) {
                SpUtil.getInstance().setDBVersion(1);
            }
        }
        initDbFlow();
        DeviceList.receiveDevPwd();
        s1.c.c().o(this);
        PushHelper.InitPush(this);
        FontHelper.Init(false, this, AppConfig.APP_FONT_NAME);
        AppVersionManager.getInstance().setDebugMode(AppVariates.debugMode);
        AppVersionManager.getInstance().init(this, AppConfig.APP_UPGRADE_ADDRESS, AppConfig.APP_UPGRADE_PORT, AppConfig.APP_ID, String.valueOf(18));
        this.filterReconnect.filter();
        AppVersionUpgradeDataHelper.INSTANCE.checkUpgradeData();
    }
```

## E003 app_log_account

Log content with account and server.

Source: `com/quvii/ubell/publico/App.java:148-151`

```java
    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ String lambda$initQvLib$1() {
        return "编译时间 " + new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.CHINA).format(new Date(BuildConfig.BUILD_TIMESTAMP)) + "\nApp application id: " + BuildConfig.APPLICATION_ID + "\nApp Version Code: 18\n账号信息 " + AppInfo.getInstance().getUsername() + "\n当前连接的服务器 " + SpUtil.getInstance().getAppServiceIp() + "\n当前的推送模式 " + SpUtil.getInstance().getAppPushMode();
    }
```

## E004 sdk_user

User initialization and credential migration.

Source: `com/quvii/ubell/publico/sdk/SdkManager.java:90-111`

```java
    private User initUser() {
        User user = new User();
        user.setAuthCode("");
        user.setType(0);
        AppInfo appInfo = AppInfo.getInstance();
        LogUtil.i("user info: " + appInfo.getUsername() + " - " + appInfo.getPassword() + " - " + appInfo.getAccountId());
        if (!TextUtils.isEmpty(appInfo.getUsername()) && !TextUtils.isEmpty(appInfo.getPassword())) {
            user.setName(appInfo.getUsername());
            user.setPassword(appInfo.getPassword());
            return user;
        }
        String userName = SpUtil.getInstance().getUserName();
        String password = SpUtil.getInstance().getPassword();
        if (TextUtils.isEmpty(userName) || TextUtils.isEmpty(password)) {
            return null;
        }
        appInfo.setUsername(userName);
        appInfo.setPassword(password);
        appInfo.save();
        SpUtil.getInstance().setUserName("");
        SpUtil.getInstance().setPassword("");
        return initUser();
```

## E005 sdk_init

P2P initialization, paths, AES and main service.

Source: `com/quvii/ubell/publico/sdk/SdkManager.java:283-318`

```java
        QvCore.getInstance().setAesKey(QvEncryptKeyStoreUtil.get());
        QvCore.getInstance().setAudioParam(true, true, false);
        String appPushToken = SpUtil.getInstance().getAppPushToken();
        int appPushMode = SpUtil.getInstance().getAppPushMode();
        LogUtil.i("CID = " + appPushToken);
        String appServiceIp = SpUtil.getInstance().getAppServiceIp();
        try {
            i2 = Integer.parseInt(SpUtil.getInstance().getAppServicePort());
        } catch (Exception e2) {
            LogUtil.e(e2.toString());
            i2 = 443;
        }
        if (TextUtils.isEmpty(appServiceIp)) {
            str5 = AppConfig.SERVER_ADDRESS;
            i3 = 443;
        } else {
            i3 = i2;
            str5 = appServiceIp;
        }
        int appDebugP2pTestTimeout = SpUtil.getInstance().getAppDebugP2pTestTimeout();
        LogUtil.i("p2p test timeout = " + appDebugP2pTestTimeout);
        QvCore.getInstance().setP2pTestTimeout(appDebugP2pTestTimeout);
        QvCore.getInstance().initParams(qvBaseApp, str5, i3, "G0021", AppConfig.APP_ID, str3, str4, string);
        QvAlarmCore.getInstance().initParams(appPushToken, 3, DataUtils.getUniqueId(qvBaseApp), LanguageUtil.initMsgNotifyLang());
        SDKVariates.setP2PManager(QvP2PClientV2.getInstance());
        setDeviceOnlineStatusListener();
        QvAlarmCore.getInstance().setUserFcmPush(appPushMode == 1);
        QvCore.getInstance().setLocalMode(true);
        QvUserAuthCore.getInstance().setRetryUser(userInitUser != null ? userInitUser.toQvUser() : null);
        QvOAuthManager.getInstance().init();
        QvCore.getInstance().start();
        if (SpUtil.getInstance().getForceForwarding()) {
            QvJniApi.SetUdpTransConn(true);
        }
        authLogin(userInitUser);
        preConnect();
```

## E006 loading_timeout

Startup timer.

Source: `com/quvii/ubell/main/presenter/LoadingPresenter.java:29-51`

```java
    private void setTimeOut() {
        Observable.timer(10L, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Long>() { // from class: com.quvii.ubell.main.presenter.LoadingPresenter.1
            @Override // io.reactivex.Observer
            public void onComplete() {
            }

            @Override // io.reactivex.Observer
            public void onError(Throwable th) {
            }

            @Override // io.reactivex.Observer
            public void onSubscribe(Disposable disposable) {
                LoadingPresenter.this.timeoutDisposable = disposable;
            }

            @Override // io.reactivex.Observer
            public void onNext(Long l2) {
                if (LoadingPresenter.this.getM() == null || LoadingPresenter.this.getV() == null) {
                    return;
                }
                LoadingPresenter.this.startActivity();
            }
        });
```

## E007 qr_pairing

Interpretation of the pairing QR code.

Source: `com/quvii/ubell/publico/sdk/DeviceHelper.java:187-210`

```java
    public static void dealWithQrInfo(String str, QrInfoCallBack qrInfoCallBack) {
        if (TextUtils.isEmpty(str)) {
            qrInfoCallBack.noFind();
            return;
        }
        DeviceQrCodeInfo deviceQrCodeInfo = getInstance().getDeviceQrCodeInfo(str);
        if (deviceQrCodeInfo == null) {
            DeviceShareInfo shareInfo = getShareInfo(str);
            if (shareInfo != null) {
                qrInfoCallBack.findDeviceShareInfo(shareInfo);
                return;
            } else {
                qrInfoCallBack.noFind();
                return;
            }
        }
        DeviceAddInfo deviceAddInfo = new DeviceAddInfo();
        deviceAddInfo.setApName(deviceQrCodeInfo.getApName());
        deviceAddInfo.setApPassword(deviceQrCodeInfo.getUid());
        deviceAddInfo.setUid(deviceQrCodeInfo.getUid());
        deviceAddInfo.setAuthCode(deviceQrCodeInfo.getAuthCode());
        deviceAddInfo.setType(deviceQrCodeInfo.getModel());
        qrInfoCallBack.findDeviceAddInfo(deviceAddInfo);
    }
```

## E008 share_parse

Invitation link fields and OEM filter.

Source: `com/quvii/ubell/publico/sdk/DeviceHelper.java:248-287`

```java
    public static DeviceShareInfo getShareInfo(String str) {
        LogUtil.i("getShareInfo: " + str);
        if (str != null && str.contains("DeviceId") && str.contains("OemId")) {
            String[] strArrSplit = str.substring(str.indexOf(Operator.Operation.EMPTY_PARAM) + 1).split(ContainerUtils.FIELD_DELIMITER);
            if (strArrSplit.length < 2) {
                return null;
            }
            DeviceShareInfo deviceShareInfo = new DeviceShareInfo();
            try {
                for (String str2 : strArrSplit) {
                    if (str2.contains("InviteCode")) {
                        deviceShareInfo.setShareCode(str2.substring(str2.indexOf("=") + 1));
                    }
                    if (str2.contains("OwnerId")) {
                        deviceShareInfo.setOwnerId(str2.substring(str2.indexOf("=") + 1));
                    }
                    if (str2.contains("OemId")) {
                        deviceShareInfo.setOemId(str2.substring(str2.indexOf("=") + 1));
                    }
                    if (str2.contains("AppVersion")) {
                        deviceShareInfo.setAppVersion(str2.substring(str2.indexOf("=") + 1));
                    }
                    if (str2.contains("DeviceId")) {
                        deviceShareInfo.setUid(str2.substring(str2.indexOf("=") + 1));
                    }
                    if (str2.contains("From")) {
                        deviceShareInfo.setFrom(str2.substring(str2.indexOf("=") + 1));
                    }
                }
                if ("G0021".contains(deviceShareInfo.getOemId())) {
                    return deviceShareInfo;
                }
                LogUtil.i("oem id error");
                return null;
            } catch (Exception e2) {
                LogUtil.printStackTrace(e2);
            }
        }
        return null;
    }
```

## E009 bind_retry

Binding retry condition.

Source: `com/quvii/ubell/publico/sdk/DeviceHelper.java:339-355`

```java
    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ boolean lambda$bindDevice$4(boolean z2, Throwable th) throws Exception {
        if (!z2 || !(th instanceof EmitterUtils.ExtError)) {
            return false;
        }
        int result = ((EmitterUtils.ExtError) th).getResult();
        if (result != 100152003 && result != 100152005) {
            return false;
        }
        try {
            Thread.sleep(5000L);
            return true;
        } catch (Exception e2) {
            LogUtil.printStackTrace(e2);
            return false;
        }
    }
```

## E010 bind_flow

Anonymous registration, invitation acceptance and timeouts.

Source: `com/quvii/ubell/publico/sdk/DeviceHelper.java:685-785`

```java
    private boolean saveDevice(QvDevice qvDevice, DeviceAddInfo deviceAddInfo) {
        return saveDevice(qvDevice, deviceAddInfo.getName(), deviceAddInfo.getAuthCode(), deviceAddInfo.getType());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public User saveUserInfo(String str, String str2) {
        User user = new User();
        user.setName(str);
        user.setAuthCode("");
        user.setPassword(str2);
        user.setType(0);
        AppVariates.setUser(user);
        AppInfo appInfo = AppInfo.getInstance();
        appInfo.setUsername(str);
        appInfo.setPassword(str2);
        appInfo.setAccountId("");
        appInfo.save();
        QvUserAuthCore.getInstance().setRetryUser(user.toQvUser());
        return user;
    }

    private Observable<Boolean> startBindDevice(final DeviceAddInfo deviceAddInfo) {
        return TextUtils.isEmpty(AppInfo.getInstance().getUsername()) ? getInstance().bindDevRegister(deviceAddInfo).map(new Function() { // from class: com.quvii.ubell.publico.sdk.c
            @Override // io.reactivex.functions.Function
            public final Object apply(Object obj) {
                return this.f9693a.lambda$startBindDevice$5(deviceAddInfo, (QvDevice) obj);
            }
        }).timeout(35L, TimeUnit.SECONDS) : getInstance().addDevice(deviceAddInfo).map(new Function() { // from class: com.quvii.ubell.publico.sdk.d
            @Override // io.reactivex.functions.Function
            public final Object apply(Object obj) {
                return this.f9697a.lambda$startBindDevice$6(deviceAddInfo, (QvDevice) obj);
            }
        }).timeout(35L, TimeUnit.SECONDS);
    }

    private void userFreeRegister(final QvDevice qvDevice, final String str, final ObservableEmitter<QvDevice> observableEmitter) {
        QvUserAuthCore.getInstance().userFreeRegister(qvDevice, str, new QvUserAuthCore.UserFreeRegisterCallBack() { // from class: com.quvii.ubell.publico.sdk.DeviceHelper.2
            @Override // com.quvii.core.QvUserAuthCore.BaseCallBack
            public void onFail(int i2) {
                EmitterUtils.onError(observableEmitter, new EmitterUtils.ExtError(null, null, i2));
            }

            @Override // com.quvii.core.QvUserAuthCore.UserFreeRegisterCallBack
            public void onSuccess(QvUserFreeRegisterResp qvUserFreeRegisterResp) {
                qvDevice.setPassword(qvUserFreeRegisterResp.getDynamicPassword());
                qvDevice.setPwdExpiredTime(qvUserFreeRegisterResp.getPasswordExpired());
                qvDevice.setDataEncodeKey(qvUserFreeRegisterResp.getDataEncodeKey());
                qvDevice.setTransparentBasedata(qvUserFreeRegisterResp.getTransparentBasedata());
                qvDevice.setIsDefaultOutAuthcode(qvUserFreeRegisterResp.getIsDefaultOutAuthcode());
                qvDevice.setDefaultOutAuthcode(qvUserFreeRegisterResp.getDefaultOutAuthCode());
                User userSaveUserInfo = DeviceHelper.this.saveUserInfo(qvUserFreeRegisterResp.getAccountName(), str);
                final QvResult qvResult = new QvResult();
                QvUserAuthCore.getInstance().userLogin(userSaveUserInfo.toQvUser(), new QvUserAuthCore.LoginCallBack() { // from class: com.quvii.ubell.publico.sdk.DeviceHelper.2.1
                    @Override // com.quvii.core.QvUserAuthCore.BaseCallBack
                    public void onFail(int i2) {
                        EmitterUtils.onError(observableEmitter, new EmitterUtils.ExtError(null, null, i2));
                    }

                    @Override // com.quvii.core.QvUserAuthCore.LoginCallBack
                    public void onSuccess(QvRespHeader qvRespHeader, QvUser qvUser) {
                        qvResult.setCode(qvRespHeader.getResult());
                        qvResult.setResult(qvUser);
                        QvUserHelper.getInstance().setLoginRet(AppInfo.getInstance().getUsername(), qvResult);
                        AppInfo appInfo = AppInfo.getInstance();
                        appInfo.setAccountId(qvUser.getId());
                        appInfo.save();
                        AnonymousClass2 anonymousClass2 = AnonymousClass2.this;
                        observableEmitter.onNext(qvDevice);
                        observableEmitter.onComplete();
                    }
                });
            }
        });
    }

    public void addOnlineListener(QvOnlineDeviceHelper.DeviceOnlineStatusListener deviceOnlineStatusListener) {
        QvCore.getInstance().addOnlineListener(deviceOnlineStatusListener);
    }

    public void addQuery(String str) {
        QvCore.getInstance().addQuery(str);
    }

    public Observable<Boolean> bindDevice(DeviceAddInfo deviceAddInfo) {
        return bindDevice(deviceAddInfo, true);
    }

    public Observable<Boolean> bindShareDevice(final DeviceShareInfo deviceShareInfo) {
        final boolean zIsEmpty = TextUtils.isEmpty(AppInfo.getInstance().getUsername());
        final String string = zIsEmpty ? UUID.randomUUID().toString() : null;
        return QvUserAuthCore.getInstance().noLoginShareInvitationAccept(deviceShareInfo.getUid(), deviceShareInfo.getShareCode(), deviceShareInfo.getName(), deviceShareInfo.getFrom(), zIsEmpty ? 1 : 0, string).flatMap(new Function() { // from class: com.quvii.ubell.publico.sdk.l
            @Override // io.reactivex.functions.Function
            public final Object apply(Object obj) {
                return this.f9728a.lambda$bindShareDevice$8(zIsEmpty, string, (NoLoginShareAcceptResp) obj);
            }
        }).map(new Function() { // from class: com.quvii.ubell.publico.sdk.v
            @Override // io.reactivex.functions.Function
            public final Object apply(Object obj) {
                return this.f9738a.lambda$bindShareDevice$9(deviceShareInfo, (NoLoginShareAcceptResp) obj);
            }
        }).timeout(35L, TimeUnit.SECONDS);
```

## E011 preview_urls

Video and audio quii URLs with credentials.

Source: `com/quvii/ubell/publico/sdk/DeviceHelper.java:846-915`

```java
    public String generatePreviewUrl(Device device) {
        if (device == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("quii://");
        sb.append(device.getUsername());
        sb.append(":");
        sb.append(device.getPassword());
        sb.append("@127.0.0.1:");
        sb.append(device.getPort());
        sb.append("/mode=real&idc=");
        sb.append(device.getCurrentChannel());
        sb.append("&ids=");
        sb.append(device.getPreviewSteam());
        sb.append(device.isConnectInner() ? "&inner=1" : "");
        return sb.toString();
    }

    public String generateShareUrl(DeviceShareInfo deviceShareInfo, String str) {
        LogUtil.i("generateShareUrl: " + deviceShareInfo.toString());
        StringBuilder sb = new StringBuilder();
        sb.append(deviceShareInfo.getUrl());
        sb.append(Operator.Operation.EMPTY_PARAM);
        if (!TextUtils.isEmpty(deviceShareInfo.getShareCode())) {
            sb.append("InviteCode=");
            sb.append(deviceShareInfo.getShareCode());
            sb.append(ContainerUtils.FIELD_DELIMITER);
        }
        if (!TextUtils.isEmpty(deviceShareInfo.getOwnerId())) {
            sb.append("OwnerId=");
            sb.append(deviceShareInfo.getOwnerId());
            sb.append(ContainerUtils.FIELD_DELIMITER);
        }
        if (!TextUtils.isEmpty(deviceShareInfo.getOemId())) {
            sb.append("OemId=");
            sb.append(deviceShareInfo.getOemId());
            sb.append(ContainerUtils.FIELD_DELIMITER);
        }
        if (!TextUtils.isEmpty(deviceShareInfo.getUid())) {
            sb.append("DeviceId=");
            sb.append(deviceShareInfo.getUid());
            sb.append(ContainerUtils.FIELD_DELIMITER);
        }
        if (!TextUtils.isEmpty(str)) {
            sb.append("From=");
            sb.append(str);
        }
        sb.append("&AppId=");
        sb.append(AppConfig.APP_ID);
        LogUtil.i("url = " + sb.toString());
        return sb.toString();
    }

    public String generateTalkUrl(Device device) {
        if (device == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("quii://");
        sb.append(device.getUsername());
        sb.append(":");
        sb.append(device.getPassword());
        sb.append("@127.0.0.1:");
        sb.append(device.getPort());
        sb.append("/talk/idc=");
        sb.append(device.getCurrentChannel());
        sb.append(device.isConnectInner() ? "&inner=1" : "");
        return sb.toString();
    }
```

## E012 qr_format

Four-field QR format.

Source: `com/quvii/ubell/publico/sdk/DeviceHelper.java:936-945`

```java
    public DeviceQrCodeInfo getDeviceQrCodeInfo(String str) {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        String[] strArrSplit = str.split(" ");
        if (strArrSplit.length < 4) {
            return null;
        }
        return new DeviceQrCodeInfo(strArrSplit[0], strArrSplit[1], strArrSplit[2], strArrSplit[3]);
    }
```

## E013 bind_save

Device persistence and initial values.

Source: `com/quvii/ubell/publico/sdk/DeviceHelper.java:1223-1267`

```java
    private boolean saveDevice(QvDevice qvDevice, String str, String str2, String str3) {
        boolean z2;
        Device device = AppDatabase.getDevice(qvDevice.getUmid());
        if (device == null) {
            device = new Device();
            device.setCid(qvDevice.getUmid());
            device.setCurrentChannel(1);
            device.setLockNum(1);
            z2 = true;
        } else {
            z2 = false;
        }
        device.setDeviceName(str);
        device.setPassword(qvDevice.getPassword());
        device.setDataEncodeKey(qvDevice.getDataEncodeKey());
        device.setTransparentBasedata(qvDevice.getTransparentBasedata());
        device.setAuthCode(str2);
        device.setPasswordExpired(qvDevice.getPwdExpiredTime());
        device.setIsDefaultOutAuthCode(qvDevice.getIsDefaultOutAuthcode());
        device.setDefaultOutAuthcode(qvDevice.getDefaultOutAuthcode());
        device.setFromShare(qvDevice.getFromShare());
        device.setPermission(Device.PERMISSION_INIT);
        device.setDeviceSwitchStateNoPasswordUnlock(true);
        device.setUnlockPassword(str2);
        if (!TextUtils.isEmpty(str3)) {
            for (String str4 : AppConfig.APP_DEVICE_AP_NAME_START) {
                if (str3.indexOf(str4) == 0) {
                    device.setDeviceModel(0);
                    break;
                }
            }
            for (String str5 : AppConfig.APP_IOT_DEVICE_AP_NAME_START) {
                if (str3.indexOf(str5) == 0) {
                    device.setDeviceModel(1);
                    break;
                }
            }
        }
        if (z2) {
            device.insert();
        } else {
            device.update();
        }
        DeviceList.updateDeviceInfo(device, false);
        return true;
```

## E014 wifi_defaults

Local AP fallback and default credentials.

Source: `com/quvii/ubell/device/add/model/DAWifiSetModel.java:51-100`

```java
        public void onNext(@NonNull List<QvDevice> list) {
            final QvDevice qvDevice;
            if (list.size() == 0) {
                qvDevice = new QvDevice();
                qvDevice.setIp("192.168.111.1");
                qvDevice.setPassword("admin");
                qvDevice.setCgiPort(443);
                qvDevice.setUsername("admin");
                LogUtil.i("发送未加密ap配置");
                DAWifiSetModel.this.sendDate(qvDevice, this.val$deviceAddInfo, new LoadListener<Throwable>() { // from class: com.quvii.ubell.device.add.model.DAWifiSetModel.1.1
                    @Override // com.quvii.publico.common.LoadListener
                    public void onResult(QvResult<Throwable> qvResult) {
                        if (qvResult.getResult() == null) {
                            AnonymousClass1.this.val$e.onNext(Integer.valueOf(qvResult.getCode()));
                            AnonymousClass1.this.val$e.onComplete();
                            return;
                        }
                        LogUtil.i("发送加密ap配置");
                        qvDevice.setUsername(SDKConst.DEVICE_USER_NAME);
                        qvDevice.setPassword(QvEncrypt.EncodeDevicePassword(AnonymousClass1.this.val$deviceAddInfo.getAuthCode()));
                        AnonymousClass1 anonymousClass1 = AnonymousClass1.this;
                        DAWifiSetModel.this.sendDate(qvDevice, anonymousClass1.val$deviceAddInfo, new LoadListener<Throwable>() { // from class: com.quvii.ubell.device.add.model.DAWifiSetModel.1.1.1
                            @Override // com.quvii.publico.common.LoadListener
                            public void onResult(QvResult<Throwable> qvResult2) {
                                if (qvResult2.getResult() != null) {
                                    AnonymousClass1.this.val$e.onError(qvResult2.getResult());
                                } else {
                                    AnonymousClass1.this.val$e.onNext(Integer.valueOf(qvResult2.getCode()));
                                    AnonymousClass1.this.val$e.onComplete();
                                }
                            }
                        });
                    }
                });
            } else {
                qvDevice = list.get(0);
                if (qvDevice.getPasswordSHA256() == 1) {
                    qvDevice.setUsername(SDKConst.DEVICE_USER_NAME);
                    qvDevice.setPassword(QvEncrypt.EncodeDevicePassword(this.val$deviceAddInfo.getAuthCode()));
                    qvDevice.setTypeOfPwdEncrypted(1);
                } else {
                    qvDevice.setUsername("admin");
                    qvDevice.setPassword("admin");
                }
                if (qvDevice.getCgiPort() <= 0) {
                    qvDevice.setCgiPort(443);
                }
            }
            DAWifiSetModel.this.sendDate(qvDevice, this.val$deviceAddInfo, new LoadListener<Throwable>() { // from class: com.quvii.ubell.device.add.model.DAWifiSetModel.1.2
                @Override // com.quvii.publico.common.LoadListener
```

## E015 wifi_payload

SSID and password encoded in Base64.

Source: `com/quvii/ubell/device/add/model/DAWifiSetModel.java:119-139`

```java
    public void sendDate(QvDevice qvDevice, DeviceAddInfo deviceAddInfo, final LoadListener<Throwable> loadListener) {
        QvDeviceCore.getInstance().setDeviceWifiInfo(qvDevice, QvBase64.encode(deviceAddInfo.getTargetName().getBytes()), QvBase64.encode(deviceAddInfo.getTargetPassword().getBytes())).subscribe(new Observer<Integer>() { // from class: com.quvii.ubell.device.add.model.DAWifiSetModel.2
            @Override // io.reactivex.Observer
            public void onComplete() {
            }

            @Override // io.reactivex.Observer
            public void onError(@NonNull Throwable th) {
                LogUtil.printStackTrace(th);
                loadListener.onResult(new QvResult(-1, th));
            }

            @Override // io.reactivex.Observer
            public void onSubscribe(@NonNull Disposable disposable) {
            }

            @Override // io.reactivex.Observer
            public void onNext(@NonNull Integer num) {
                loadListener.onResult(new QvResult(num.intValue()));
            }
        });
```

## E016 voice_payload

Frequencies and acoustic emission of pairing.

Source: `com/quvii/ubell/device/add/model/DAVoiceSetModel.java:48-81`

```java
    private void init() {
        int[] iArr = new int[19];
        for (int i2 = 0; i2 < 19; i2++) {
            iArr[i2] = 4000 + (i2 * 150);
        }
        VoicePlayer voicePlayer = new VoicePlayer();
        this.player = voicePlayer;
        voicePlayer.setFreqs(iArr);
    }

    @Override // com.quvii.ubell.device.add.contract.DAVoiceSetContract.Model
    public int getDeviceState(String str) {
        return DeviceHelper.getInstance().getDirectDeviceStatus(str);
    }

    @Override // com.quvii.ubell.device.add.contract.DAVoiceSetContract.Model
    public Observable<Integer> getDeviceStateWithLocal(String str) {
        return DeviceHelper.getInstance().getDeviceStateWithLocal(str);
    }

    @Override // com.quvii.ubell.device.add.contract.DAVoiceSetContract.Model
    public Observable<Integer> getDeviceStateWithService(String str) {
        return DeviceHelper.getInstance().getDeviceStateWithService(str);
    }

    @Override // com.quvii.ubell.device.add.contract.DAVoiceSetContract.Model
    public void voicePlay(String str, String str2) {
        LogUtil.i("voicePlay");
        if (this.player == null) {
            init();
        }
        QvOnlineDeviceHelper.getInstance().setLanSearchTime(150);
        this.player.play(DataEncoder.encodeSSIDWiFi(str, str2), 999, 2000);
    }
```

## E017 wifi_timeout

Countdown and pairing conditions.

Source: `com/quvii/ubell/device/add/presenter/DAWifiSetPresenter.java:156-200`

```java
    /* JADX INFO: Access modifiers changed from: private */
    public void countDownSwitch(boolean z2) {
        LogUtil.i("countDownSwitch: " + z2);
        if (!z2) {
            QvCountDownUtil qvCountDownUtil = this.countDownUtil;
            if (qvCountDownUtil != null) {
                qvCountDownUtil.release();
                return;
            }
            return;
        }
        if (this.countDownUtil == null) {
            this.countDownUtil = new QvCountDownUtil();
        }
        getV().showCountdownInfo(TIME_OUT);
        this.countDownUtil.setTimeOut(TIME_OUT);
        this.countDownUtil.setListener(new QvCountDownUtil.Listener() { // from class: com.quvii.ubell.device.add.presenter.DAWifiSetPresenter.3
            @Override // com.quvii.qvlib.util.QvCountDownUtil.Listener
            public void onComplete() {
            }

            @Override // com.quvii.qvlib.util.QvCountDownUtil.Listener
            public void onCount(int i2) {
                if (i2 == 0) {
                    DAWifiSetPresenter.this.setConfigFail();
                }
                if (!DAWifiSetPresenter.this.isViewAttached()) {
                    DAWifiSetPresenter.this.countDownSwitch(false);
                    return;
                }
                if (i2 == 60 && DAWifiSetPresenter.this.isAlwaysOnline && DeviceHelper.getInstance().checkP2POnline(DAWifiSetPresenter.this.onlineStatus)) {
                    DAWifiSetPresenter.this.dealWithDeviceOnline();
                }
                if (i2 < 110) {
                    DAWifiSetPresenter.this.getV().showOrHideNetError(!DownChannelManager.getInstance().isConnected());
                }
                if (i2 > 0) {
                    DAWifiSetPresenter.this.getV().showCountdownInfo(i2);
                } else {
                    DAWifiSetPresenter.this.configStatus = -2;
                    DAWifiSetPresenter.this.getV().showConfigStatus(false, DAWifiSetPresenter.this.configStatus);
                }
            }
        });
        this.countDownUtil.start();
```

## E018 wifi_retry

Status polling and network recovery.

Source: `com/quvii/ubell/device/add/presenter/DAWifiSetPresenter.java:243-294`

```java
    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$queryDeviceOnlineState$0(String str, ObservableEmitter observableEmitter) throws Exception {
        while (!observableEmitter.isDisposed() && getM() != null) {
            int deviceState = getM().getDeviceState(str);
            LogUtil.i("state = " + deviceState);
            this.onlineStatus = deviceState;
            if (deviceState == 0 || deviceState == 2) {
                this.isAlwaysOnline = false;
            } else if (DeviceHelper.getInstance().checkP2POnline(deviceState) && !this.isAlwaysOnline) {
                observableEmitter.onNext(0);
                observableEmitter.onComplete();
            }
            try {
                Thread.sleep(2000L);
            } catch (Exception unused) {
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Observable<Integer> queryDeviceOnlineState(final String str) {
        LogUtil.i("queryDeviceOnlineState");
        return Observable.create(new ObservableOnSubscribe() { // from class: com.quvii.ubell.device.add.presenter.g
            @Override // io.reactivex.ObservableOnSubscribe
            public final void subscribe(ObservableEmitter observableEmitter) throws Exception {
                this.f9379a.lambda$queryDeviceOnlineState$0(str, observableEmitter);
            }
        }).subscribeOn(Schedulers.io());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setConfigFail() {
        LogUtil.i("setConfigFail current status: " + this.configStatus);
        countDownSwitch(false);
        showStatus(-1);
        configStop();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setConnectFail() {
        LogUtil.i("setConnectFail");
        if (isViewAttached()) {
            if (this.deviceAddInfo.getRetryCount() > 0) {
                LogUtil.i("retry: " + this.deviceAddInfo.getRetryCount());
                DeviceAddInfo deviceAddInfo = this.deviceAddInfo;
                deviceAddInfo.setRetryCount(deviceAddInfo.getRetryCount() + (-1));
                getV().showNetworkError();
            }
            setConfigFail();
        }
    }
```

## E019 device_actions

Unlock and F1 with biometrics or password.

Source: `com/quvii/ubell/main/presenter/MainDeviceListPresenter.java:190-258`

```java
    @Override // com.quvii.ubell.main.contract.MainDeviceListContract.Presenter
    public void checkDeviceF1(final Device device) {
        if (!device.getDevicePermissionInfo().allowF1Function()) {
            getV().showMessage(R.string.key_device_feature_not_support);
            return;
        }
        if (device.isDeviceSwitchStateBiologicalUnlock()) {
            SystemUtil.requestBiologicalVerify(this.biometricUtil, true, new SystemUtil.BiometricVerifyCallBack() { // from class: com.quvii.ubell.main.presenter.MainDeviceListPresenter.4
                @Override // com.quvii.ubell.publico.util.SystemUtil.BiometricVerifyCallBack
                public void onFail(String str) {
                    MainDeviceListPresenter.this.getV().showMessage(str);
                }

                @Override // com.quvii.ubell.publico.util.SystemUtil.BiometricVerifyCallBack
                public void onLockout(int i2) {
                    MainDeviceListPresenter.this.getV().showF1Verify(device);
                }

                @Override // com.quvii.ubell.publico.util.SystemUtil.BiometricVerifyCallBack
                public void onSuccess() {
                    MainDeviceListPresenter.this.startDirectEnableF1(device);
                }

                @Override // com.quvii.ubell.publico.util.SystemUtil.BiometricVerifyCallBack
                public void onTextClick() {
                    MainDeviceListPresenter.this.getV().showF1Verify(device);
                }
            });
        } else if (device.isDeviceSwitchStateNoPasswordUnlock()) {
            startDirectEnableF1(device);
        } else {
            getV().showF1Verify(device);
        }
    }

    @Override // com.quvii.ubell.main.contract.MainDeviceListContract.Presenter
    public void checkDeviceUnlock(final Device device) {
        if (!device.getDeviceAbility().isSupportUnlock() || !device.getDevicePermissionInfo().allowUnlock()) {
            getV().showMessage(R.string.key_device_feature_not_support);
            return;
        }
        if (device.isDeviceSwitchStateBiologicalUnlock()) {
            SystemUtil.requestBiologicalVerify(this.biometricUtil, true, new SystemUtil.BiometricVerifyCallBack() { // from class: com.quvii.ubell.main.presenter.MainDeviceListPresenter.7
                @Override // com.quvii.ubell.publico.util.SystemUtil.BiometricVerifyCallBack
                public void onFail(String str) {
                    MainDeviceListPresenter.this.getV().showMessage(str);
                }

                @Override // com.quvii.ubell.publico.util.SystemUtil.BiometricVerifyCallBack
                public void onLockout(int i2) {
                    MainDeviceListPresenter.this.getV().showEnterUnlockPassword(device);
                }

                @Override // com.quvii.ubell.publico.util.SystemUtil.BiometricVerifyCallBack
                public void onSuccess() {
                    MainDeviceListPresenter.this.startDirectUnlockLock1(device);
                }

                @Override // com.quvii.ubell.publico.util.SystemUtil.BiometricVerifyCallBack
                public void onTextClick() {
                    MainDeviceListPresenter.this.getV().showEnterUnlockPassword(device);
                }
            });
        } else if (device.isDeviceSwitchStateNoPasswordUnlock()) {
            startDirectUnlockLock1(device);
        } else {
            getV().showEnterUnlockPassword(device);
        }
    }
```

## E020 device_list

Empty, list and refresh device states.

Source: `com/quvii/ubell/main/presenter/MainDeviceListPresenter.java:283-333`

```java
    @Override // com.quvii.ubell.main.contract.MainDeviceListContract.Presenter
    public void getDeviceList() {
        LogUtil.i("getDeviceList");
        List<Device> deviceList = getM().getDeviceList();
        DeviceList.setDeviceList(deviceList);
        User user = AppVariates.getUser();
        if (user != null) {
            user.setDeviceCount(deviceList.size());
        }
        if (deviceList.size() == 0) {
            getV().showRefreshing(false);
            getV().hideDeviceList();
            getV().showCenterAddView();
        } else {
            getV().hideCenterAddView();
            getV().showDeviceList(DeviceList.mList);
        }
        getM().getAuthServerDevList(new MainDeviceListContract.AuthInfoCallBack() { // from class: com.quvii.ubell.main.presenter.c
            @Override // com.quvii.ubell.main.contract.MainDeviceListContract.AuthInfoCallBack
            public final void onQuery(List list) {
                this.f9597a.lambda$getDeviceList$1(list);
            }
        });
    }

    @Override // com.quvii.ubell.main.contract.MainDeviceListContract.Presenter
    public void getDeviceState() {
        boolean z2 = true;
        getV().showRefreshing(true);
        if (this.isQueryState) {
            return;
        }
        this.isQueryState = true;
        getM().getDeviceState(DeviceList.mList).timeout(60L, TimeUnit.SECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe(new MyObserver<Integer>(this.mDisposable, this, z2) { // from class: com.quvii.ubell.main.presenter.MainDeviceListPresenter.2
            @Override // com.quvii.ubell.publico.base.MyObserver
            public void onMyComplete() {
                MainDeviceListPresenter.this.isQueryState = false;
                MainDeviceListPresenter.this.getV().showRefreshing(false);
                MainDeviceListPresenter.this.getV().showDeviceList(DeviceList.mList);
            }

            @Override // com.quvii.ubell.publico.base.MyObserver
            public void onMyError(Throwable th) {
                MainDeviceListPresenter.this.isQueryState = false;
                LogUtil.printStackTrace(th);
                if (MainDeviceListPresenter.this.isViewAttached()) {
                    MainDeviceListPresenter.this.getV().showRefreshing(false);
                    MainDeviceListPresenter.this.getV().showDeviceList(DeviceList.mList);
                }
            }
        });
```

## E021 preview_start

P2P opening and playback with data key.

Source: `com/quvii/ubell/video/model/PreviewModel.java:126-182`

```java
    public /* synthetic */ void lambda$startPlay$3(PlayerItem playerItem, int i2) {
        Boolean devicePort;
        Device device = playerItem.getDevice();
        LogUtil.i("get port start");
        do {
            devicePort = DeviceList.getDevicePort(device, true);
            LogUtil.i("getPortRet = " + devicePort);
            if (devicePort != null) {
                if (!devicePort.booleanValue()) {
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException unused) {
                        return;
                    }
                }
                if (!playerItem.isPlaying) {
                    break;
                }
            } else {
                LogUtil.i("port is null");
                break;
            }
        } while (!devicePort.booleanValue());
        int cgiPort = device.getCgiPort();
        LogUtil.e("Port---------" + device.getCid() + "--->" + device.getPort());
        LogUtil.e("cgiPort---------" + device.getCid() + "--->" + cgiPort);
        final int devConnMode = QvJniApi.getDevConnMode(playerItem.getDevice().getCid());
        StringBuilder sb = new StringBuilder();
        sb.append("连接设备方式--------- ");
        sb.append(devConnMode);
        LogUtil.e(sb.toString());
        if (!playerItem.isPlaying) {
            LogUtil.i("return");
            return;
        }
        if (this.onConnectTypeShowListener != null) {
            RxJavaUtils.Wait(0, new RxJavaUtils.WaitCallBack() { // from class: com.quvii.ubell.video.model.d
                @Override // com.quvii.qvweb.publico.utils.RxJavaUtils.WaitCallBack
                public final void onWait() {
                    this.f9844a.lambda$startPlay$2(devConnMode);
                }
            });
        }
        if (device.getDeviceModel() != 1) {
            device.setPreviewSteam(2);
        } else {
            device.setPreviewSteam(devConnMode != 3 ? 2 : 1);
        }
        String strGeneratePreviewUrl = DeviceHelper.getInstance().generatePreviewUrl(device);
        String dataEncodeKey = device.getDataEncodeKey();
        LogUtil.i("key = " + dataEncodeKey);
        if (TextUtils.isEmpty(dataEncodeKey)) {
            LogUtil.e("data encode key is null!");
            return;
        }
        LogUtil.i("result = " + PreviewVideoPlayer.getInstance().startPlay(i2, strGeneratePreviewUrl, dataEncodeKey));
    }
```

## E022 preview_password

Renewal of an expired dynamic password.

Source: `com/quvii/ubell/video/model/PreviewModel.java:241-272`

```java
    public int play(final int i2, final PlayerItem playerItem) {
        final Device device = playerItem.getDevice();
        String password = device.getPassword();
        if (password == null || password.length() == 0) {
            return -1;
        }
        long stringToUtcDate = QvDateUtil.getStringToUtcDate(playerItem.getDevice().getPasswordExpired()) - System.currentTimeMillis();
        LogUtil.i("expired time: " + stringToUtcDate);
        if (stringToUtcDate >= 0) {
            startPlay(i2, playerItem);
            return 0;
        }
        LogUtil.e("password is expired");
        Observable.create(new ObservableOnSubscribe() { // from class: com.quvii.ubell.video.model.c
            @Override // io.reactivex.ObservableOnSubscribe
            public final void subscribe(ObservableEmitter observableEmitter) throws Exception {
                this.f9842a.lambda$play$1(device, observableEmitter);
            }
        }).timeout(20L, TimeUnit.SECONDS).subscribe(new SimpleObserver<Integer>() { // from class: com.quvii.ubell.video.model.PreviewModel.1
            @Override // com.quvii.ubell.publico.base.SimpleObserver, io.reactivex.Observer
            public void onError(Throwable th) {
                super.onError(th);
                LogUtil.e("time out");
                PreviewModel.this.stop(i2);
            }

            @Override // io.reactivex.Observer
            public void onNext(Integer num) {
                PreviewModel.this.startPlay(i2, playerItem);
            }
        });
        return 0;
```

## E023 talk_audio

Listen and talk over AudioPlayerManager.

Source: `com/quvii/ubell/video/model/PreviewModel.java:322-379`

```java
    public void talkSwitch(boolean z2, Device device, PreviewContract.OnControlListener onControlListener) {
        synchronized (this) {
            try {
                if (device == null) {
                    return;
                }
                String strGenerateTalkUrl = DeviceHelper.getInstance().generateTalkUrl(device);
                if (z2) {
                    AudioPlayerManager.getInstance().startTalking(strGenerateTalkUrl, device.getDataEncodeKey());
                } else {
                    AudioPlayerManager.getInstance().stopTalking(strGenerateTalkUrl);
                }
                onControlListener.onControl(Boolean.valueOf(z2));
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    @Override // com.quvii.ubell.video.contract.PreviewContract.Model
    public void unlock(int i2, int i3, int i4, boolean z2, String str) {
        sendDeviceOrder(i2, 4, DeviceOrderHelper.SendUnlockData(i3, i4, z2, str));
    }

    @Override // com.quvii.ubell.video.contract.PreviewContract.Model
    public void voiceSwitch(boolean z2, final PlayerItem playerItem, final PreviewContract.OnControlListener onControlListener) {
        this.voiceExecutorService.execute(new MyRunnable(z2) { // from class: com.quvii.ubell.video.model.PreviewModel.3
            @Override // com.quvii.ubell.video.model.PreviewModel.MyRunnable, java.lang.Runnable
            public void run() {
                PreviewModel.this.voiceListener = onControlListener;
                if (this.toggle) {
                    int playerState = PreviewVideoPlayer.getInstance().getPlayerState(playerItem.tag);
                    for (int i2 = 0; playerState != 4 && i2 < 15; i2++) {
                        SystemClock.sleep(1000L);
                        playerState = PreviewVideoPlayer.getInstance().getPlayerState(playerItem.tag);
                    }
                    AudioPlayerManager.getInstance().startListening(playerItem.tag);
                } else {
                    AudioPlayerManager.getInstance().stopListening(playerItem.tag);
                }
                Message messageObtain = Message.obtain();
                messageObtain.obj = Boolean.valueOf(this.toggle);
                PreviewModel.this.mVoiceHandler.sendMessage(messageObtain);
            }
        });
    }

    @Override // com.quvii.ubell.video.contract.PreviewContract.Model
    public void talkSwitch(boolean z2, Device device) {
        if (device == null) {
            return;
        }
        String strGenerateTalkUrl = DeviceHelper.getInstance().generateTalkUrl(device);
        if (z2) {
            AudioPlayerManager.getInstance().startTalking(strGenerateTalkUrl, device.getDataEncodeKey());
        } else {
            AudioPlayerManager.getInstance().stopTalking(strGenerateTalkUrl);
        }
```

## E024 preview_timeout

Auto power-on timer.

Source: `com/quvii/ubell/video/presenter/PreviewPresenter.java:513-550`

```java
    private void previewTimeout(final int i2, boolean z2) {
        Disposable disposable = this.disposablesPreviewTimeout[i2];
        if (disposable != null && !disposable.isDisposed()) {
            this.disposablesPreviewTimeout[i2].dispose();
        }
        if (isViewAttached()) {
            getV().showPreviewTime(Boolean.valueOf(z2), getPreviewTimeout());
        }
        if (z2) {
            Observable.interval(1L, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new SimpleObserver<Long>() { // from class: com.quvii.ubell.video.presenter.PreviewPresenter.7
                final int max;

                {
                    this.max = PreviewPresenter.this.getPreviewTimeout() - 1;
                }

                @Override // com.quvii.ubell.publico.base.SimpleObserver, io.reactivex.Observer
                public void onSubscribe(Disposable disposable2) {
                    super.onSubscribe(disposable2);
                    PreviewPresenter.this.disposablesPreviewTimeout[i2] = disposable2;
                }

                @Override // io.reactivex.Observer
                public void onNext(Long l2) {
                    int iLongValue = (int) (((long) this.max) - l2.longValue());
                    if (PreviewPresenter.this.isViewAttached()) {
                        PreviewPresenter.this.getV().showPreviewTime(null, iLongValue);
                    }
                    if (iLongValue <= 0) {
                        PreviewPresenter.this.previewSwitch(i2, false);
                        if (PreviewPresenter.this.isViewAttached()) {
                            PreviewPresenter.this.getV().showPreviewInfo(i2, 104);
                        }
                    }
                }
            });
        }
    }
```

## E025 unlock_timeout

Sending and opening timeout.

Source: `com/quvii/ubell/video/presenter/PreviewPresenter.java:673-696`

```java
    /* JADX INFO: Access modifiers changed from: private */
    public void startUnlock(int i2, String str, final Device device) {
        if (checkDevicePreview(this.focusPosition)) {
            showUnlockState(device, 1);
            if (device.getDeviceAbility().isSupportPasswordSHA256()) {
                str = QvEncrypt.EncodeDevicePassword(str);
            }
            String str2 = str;
            LogUtil.i("un: " + str2);
            getM().unlock(this.focusPosition, i2, device.getCurrentChannel(), device.getDeviceAbility().isSupportPasswordSHA256(), str2);
            this.compositeDisposableUnlock.clear();
            Observable.timer(15L, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new SimpleObserver<Long>() { // from class: com.quvii.ubell.video.presenter.PreviewPresenter.2
                @Override // com.quvii.ubell.publico.base.SimpleObserver, io.reactivex.Observer
                public void onSubscribe(Disposable disposable) {
                    super.onSubscribe(disposable);
                    PreviewPresenter.this.compositeDisposableUnlock.add(disposable);
                }

                @Override // io.reactivex.Observer
                public void onNext(Long l2) {
                    PreviewPresenter.this.showUnlockState(device, 4);
                }
            });
        }
```

## E026 preview_retry

Preview resumption.

Source: `com/quvii/ubell/video/presenter/PreviewPresenter.java:948-965`

```java
    @Override // com.quvii.ubell.video.contract.PreviewContract.Presenter
    public void previewSwitch(int i2) {
        if (checkDevice(i2)) {
            previewSwitch(i2, !this.playerItemList.get(i2).isPlaying);
        }
    }

    @Override // com.quvii.ubell.video.contract.PreviewContract.Presenter
    public void rePreview() {
        LogUtil.i("rePreview");
        for (int i2 = 0; i2 < this.playerItemList.size(); i2++) {
            PlayerItem playerItem = this.playerItemList.get(i2);
            if (playerItem != null) {
                previewSwitch(i2, false);
                playerItem.setNeedResume(3);
            }
        }
    }
```

## E027 http_logging

HTTP BODY logging in release.

Source: `com/quvii/qvweb/publico/utils/OkHttpUtil.java:67-90`

```java
    static {
        TimeUnit timeUnit = TimeUnit.MINUTES;
        commonConnectionPool = new ConnectionPool(5, 5L, timeUnit);
        channelConnectionPool = new ConnectionPool(5, 5L, timeUnit);
        QvHttpLoggingInterceptor qvHttpLoggingInterceptor = new QvHttpLoggingInterceptor(new QvHttpLoggingInterceptor.Logger() { // from class: com.quvii.qvweb.publico.utils.d
            @Override // com.quvii.qvweb.publico.utils.QvHttpLoggingInterceptor.Logger
            public final void log(String str) {
                OkHttpUtil.printLog(str);
            }
        });
        QvHttpLoggingInterceptor.Level level = QvHttpLoggingInterceptor.Level.BODY;
        interceptor = qvHttpLoggingInterceptor.setLevel(level);
        cgiInterceptor = SDKConfig.DEBUG_HTTP ? new HttpLoggingInterceptor(new e()).setLevel(HttpLoggingInterceptor.Level.BODY) : new QvHttpLoggingInterceptor(new QvHttpLoggingInterceptor.Logger() { // from class: com.quvii.qvweb.publico.utils.f
            @Override // com.quvii.qvweb.publico.utils.QvHttpLoggingInterceptor.Logger
            public final void log(String str) {
                OkHttpUtil.printCgiLog(str);
            }
        }).setLevel(level);
        qvAuthHttpLoggingInterceptor = new QvAuthHttpLoggingInterceptor(new QvAuthHttpLoggingInterceptor.Logger() { // from class: com.quvii.qvweb.publico.utils.g
            @Override // com.quvii.qvweb.publico.utils.QvAuthHttpLoggingInterceptor.Logger
            public final void log(String str) {
                OkHttpUtil.printCgiLog(str);
            }
        }).setLevel(QvAuthHttpLoggingInterceptor.Level.BODY);
```

## E028 http_bks

Loading of BKS stores and embedded passwords.

Source: `com/quvii/qvweb/publico/utils/OkHttpUtil.java:367-389`

```java
    private static void initSSL() {
        if (sslSocketFactory == null || x509TrustManager == null) {
            try {
                KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
                KeyStore keyStore2 = KeyStore.getInstance(KEYSTORE_TYPE);
                InputStream inputStreamOpen = SDKVariates.applicationContext.getAssets().open(QvCore.CLIENT_PRI_KEY);
                InputStream inputStreamOpen2 = SDKVariates.applicationContext.getAssets().open(QvCore.TRUSTSTORE_PUB_KEY);
                keyStore.load(inputStreamOpen, "123456".toCharArray());
                keyStore2.load(inputStreamOpen2, "123456".toCharArray());
                inputStreamOpen.close();
                inputStreamOpen2.close();
                SSLContext sSLContext = SSLContext.getInstance(PROTOCOL_TYPE);
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(CERTIFICATE_FORMAT);
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(CERTIFICATE_FORMAT);
                trustManagerFactory.init(keyStore2);
                keyManagerFactory.init(keyStore, "123456".toCharArray());
                sSLContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
                sslSocketFactory = sSLContext.getSocketFactory();
                x509TrustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
            } catch (Exception e2) {
                LogUtil.printStackTrace(e2);
            }
        }
```

## E029 http_token

Token header for OAuth APIs.

Source: `com/quvii/qvweb/publico/utils/OkHttpUtil.java:406-414`

```java
    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ Response lambda$getFileTransTokenInstance$1(Interceptor.Chain chain) throws IOException {
        return chain.proceed(chain.request().newBuilder().addHeader("token", token).build());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ Response lambda$getTokenInstance$0(Interceptor.Chain chain) throws IOException {
        return chain.proceed(chain.request().newBuilder().addHeader("token", token).build());
    }
```

## E030 http_trust_all

Permissive HostnameVerifier and empty TrustManager.

Source: `com/quvii/qvweb/publico/utils/OkHttpUtil.java:562-588`

```java
    public static OkHttpClient.Builder createBuilder(int i2) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        long j2 = i2;
        TimeUnit timeUnit = TimeUnit.SECONDS;
        OkHttpClient.Builder builderRetryOnConnectionFailure = builder.connectTimeout(j2, timeUnit).readTimeout(j2, timeUnit).writeTimeout(j2, timeUnit).connectionPool(commonConnectionPool).hostnameVerifier(new AllowAllHostnameVerifier()).retryOnConnectionFailure(true);
        try {
            X509TrustManager x509TrustManager2 = new X509TrustManager() { // from class: com.quvii.qvweb.publico.utils.OkHttpUtil.1
                @Override // javax.net.ssl.X509TrustManager
                public void checkClientTrusted(X509Certificate[] x509CertificateArr, String str) throws CertificateException {
                }

                @Override // javax.net.ssl.X509TrustManager
                public void checkServerTrusted(X509Certificate[] x509CertificateArr, String str) throws CertificateException {
                }

                @Override // javax.net.ssl.X509TrustManager
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };
            SSLContext sSLContext = SSLContext.getInstance(PROTOCOL_TYPE);
            sSLContext.init(null, new TrustManager[]{x509TrustManager2}, null);
            builderRetryOnConnectionFailure.sslSocketFactory(sSLContext.getSocketFactory(), x509TrustManager2);
        } catch (Exception e2) {
            LogUtil.printStackTrace(e2);
        }
        return builderRetryOnConnectionFailure;
```

## E031 digest_auth

Device Digest MD5.

Source: `com/quvii/qvweb/publico/intercept/DeviceAuthHeaderInterceptor.java:24-44`

```java
    public String digestAuthForUsername(QvDevice qvDevice, Map<String, String> map) {
        String str;
        String username = qvDevice.getUsername();
        String password = qvDevice.getPassword();
        String dataEncodeKey = qvDevice.getDataEncodeKey();
        String str2 = map.get("Digest realm");
        String str3 = map.get("nonce");
        String str4 = map.get("opaque");
        String str5 = map.get("qop");
        if (TextUtils.isEmpty(str2) || TextUtils.isEmpty(str3) || TextUtils.isEmpty(str4) || TextUtils.isEmpty(str5)) {
            return "";
        }
        if (qvDevice.isHsDevice()) {
            str = username + ":" + str2 + ":" + password;
        } else {
            if (username.equals("adminapp")) {
                username = QvDeviceSDK.getInstance().sslAes256EncryptForAuthVSU(password, dataEncodeKey);
            }
            str = username + ":" + str2 + ":" + dataEncodeKey;
        }
        return String.format("Digest username=\"%s\",realm=\"%s\",nonce=\"%s\",uri=\"%s\",algorithm=%s,response=\"%s\",opaque=\"%s\",qop=%s,nc=%s,cnonce=\"%s\"", username, str2, str3, HttpDeviceConst.CGI_ADDRESS, "MD5", QvEncrypt.getMD5(QvEncrypt.getMD5(str) + ":" + str3 + ":nc00001:suiji:" + str5 + ":" + QvEncrypt.getMD5("POST:" + HttpDeviceConst.CGI_ADDRESS)), str4, str5, "nc00001", "suiji");
```

## E032 digest_retry

401 response and second Authorization attempt.

Source: `com/quvii/qvweb/publico/intercept/DeviceAuthHeaderInterceptor.java:47-93`

```java
    @Override // okhttp3.Interceptor
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        Response responseProceed = chain.proceed(request);
        if (responseProceed.code() != 401) {
            return responseProceed;
        }
        Headers headers = responseProceed.headers();
        StringBuilder sb = new StringBuilder();
        for (int i2 = 0; i2 < headers.size(); i2++) {
            if ("WWW-Authenticate".equals(headers.name(i2))) {
                sb.append(",");
                sb.append(headers.value(i2));
            }
        }
        if (sb.toString().startsWith(",")) {
            sb = new StringBuilder(sb.substring(1));
        }
        if (TextUtils.isEmpty(sb)) {
            return responseProceed;
        }
        for (String str : sb.toString().split(",")) {
            String[] strArrSplit = str.split("=", 2);
            if (strArrSplit.length == 2) {
                this.headerMap.put(strArrSplit[0].trim().replaceAll("\"", ""), strArrSplit[1].trim().replaceAll("\"", ""));
            }
        }
        QvDevice qvDevice = this.device;
        if (qvDevice == null) {
            return responseProceed;
        }
        String strDigestAuthForUsername = digestAuthForUsername(qvDevice, this.headerMap);
        if (TextUtils.isEmpty(strDigestAuthForUsername)) {
            return responseProceed;
        }
        Request.Builder builderNewBuilder = request.newBuilder();
        builderNewBuilder.addHeader("Authorization", strDigestAuthForUsername);
        Request requestBuild = builderNewBuilder.build();
        SDKVariates.authHeaderInterceptorMap.remove(this.device.getUmid());
        SDKVariates.deviceAuthOkHttpClient.remove(this.device.getUmid());
        Response responseProceed2 = chain.proceed(requestBuild);
        this.isJump = requestBuild.headers().names().contains("Authorization");
        if (responseProceed2.code() != 401 || !this.isJump) {
            return responseProceed2;
        }
        this.isJump = false;
        return responseProceed2.newBuilder().code(IPhotoView.DEFAULT_ZOOM_DURATION).build();
```

## E033 oauth_grant

JWT, password grant and token storage.

Source: `com/quvii/qvweb/oauth/QvOAuthManager.java:74-113`

```java
    private long getTimeout(String str) {
        if (str != null) {
            try {
                return ((QvTokenTimeInfo) this.gson.fromJson(new String(Base64.decode(str.split("\\.")[1], 8)), QvTokenTimeInfo.class)).getExp() * 1000;
            } catch (Exception e2) {
                LogUtil.printStackTrace(e2);
            }
        }
        return 0L;
    }

    private Observable<GetTokenResp> getToken(String str, String str2) {
        final LinkedHashMap linkedHashMap = new LinkedHashMap();
        linkedHashMap.put("grant_type", "password");
        linkedHashMap.put("client_id", SDKVariates.ALARM_CLIENT_ID);
        linkedHashMap.put("client_type", String.valueOf(SDKVariates.CLIENT_TYPE));
        linkedHashMap.put("oemid", SDKVariates.OEM_ID);
        linkedHashMap.put("appid", String.valueOf(SDKVariates.APP_ID));
        linkedHashMap.put("usr", str);
        linkedHashMap.put("pwd", str2);
        linkedHashMap.put("region_id", String.valueOf(QvLocationManager.getInstance().getCurrentRegionId()));
        linkedHashMap.put("client_flag", SDKConfig.isNoLoginMode() ? "2" : "1");
        return getApi().flatMap(new Function() { // from class: com.quvii.qvweb.oauth.h
            @Override // io.reactivex.functions.Function
            public final Object apply(Object obj) {
                return QvOAuthManager.lambda$getToken$5(linkedHashMap, (OAuthApi) obj);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ String lambda$dealWithTokenResp$4(String str, String str2, String str3, GetTokenResp getTokenResp) throws Exception {
        String access_token = getTokenResp.getAccess_token();
        String refresh_token = getTokenResp.getRefresh_token();
        long jCurrentTimeMillis = System.currentTimeMillis();
        long timeout = getTimeout(access_token);
        long timeout2 = getTimeout(refresh_token);
        LogUtil.i("current: " + jCurrentTimeMillis + " ,access: " + timeout + " ,time: " + (timeout - jCurrentTimeMillis) + " ,refresh: " + timeout2 + " ,time: " + (timeout2 - jCurrentTimeMillis));
        QvOAuthSpUtil.getInstance().setAccountInfo(str, str2, str3, access_token, refresh_token);
        return access_token;
```

## E034 oauth_refresh

Cache, refresh and discovered endpoint.

Source: `com/quvii/qvweb/oauth/QvOAuthManager.java:151-219`

```java
    public /* synthetic */ void lambda$startGetAccessToken$1(String str, String str2, String str3, ObservableEmitter observableEmitter) throws Exception {
        if (!Objects.equals(this.currentUrl, str)) {
            this.currentUrl = str;
            this.oAuthApi = null;
        }
        if (TextUtils.isEmpty(this.currentUrl) || TextUtils.isEmpty(str2) || TextUtils.isEmpty(str3)) {
            EmitterUtils.onError(observableEmitter, SDKStatus.FAIL_STATUS_ERROR);
            return;
        }
        String authAccessToken = QvOAuthSpUtil.getInstance().getAuthAccessToken(str2);
        if (!TextUtils.isEmpty(authAccessToken)) {
            if (getTimeout(authAccessToken) - System.currentTimeMillis() > 300000) {
                LogUtil.i("use cache token");
                observableEmitter.onNext(authAccessToken);
                observableEmitter.onComplete();
                return;
            } else if (getTimeout(QvOAuthSpUtil.getInstance().getAuthRefreshToken(str2)) - System.currentTimeMillis() > 300000) {
                observableEmitter.onNext(ORDER_REFRESH_TOKEN);
                observableEmitter.onComplete();
                return;
            }
        }
        observableEmitter.onNext(ORDER_GET_TOKEN);
        observableEmitter.onComplete();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ ObservableSource lambda$startGetAccessToken$2(String str, String str2, String str3, String str4) throws Exception {
        str4.hashCode();
        if (str4.equals(ORDER_REFRESH_TOKEN)) {
            return refreshToken(QvOAuthSpUtil.getInstance().getAuthRefreshToken(str3)).map(dealWithTokenResp(str3, str, str2));
        }
        return !str4.equals(ORDER_GET_TOKEN) ? Observable.just(str4) : getToken(str, str2).map(dealWithTokenResp(str3, str, str2));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ ObservableSource lambda$startGetAccessToken$3(final String str, final String str2, final String str3, final String str4) throws Exception {
        return Observable.create(new ObservableOnSubscribe() { // from class: com.quvii.qvweb.oauth.b
            @Override // io.reactivex.ObservableOnSubscribe
            public final void subscribe(ObservableEmitter observableEmitter) throws Exception {
                this.f9291a.lambda$startGetAccessToken$1(str4, str, str2, observableEmitter);
            }
        }).flatMap(new Function() { // from class: com.quvii.qvweb.oauth.c
            @Override // io.reactivex.functions.Function
            public final Object apply(Object obj) {
                return this.f9295a.lambda$startGetAccessToken$2(str2, str3, str, (String) obj);
            }
        });
    }

    private Observable<GetTokenResp> refreshToken(String str) {
        final LinkedHashMap linkedHashMap = new LinkedHashMap();
        linkedHashMap.put("grant_type", "refresh_token");
        linkedHashMap.put("refresh_token", str);
        return getApi().flatMap(new Function() { // from class: com.quvii.qvweb.oauth.d
            @Override // io.reactivex.functions.Function
            public final Object apply(Object obj) {
                return QvOAuthManager.lambda$refreshToken$6(linkedHashMap, (OAuthApi) obj);
            }
        });
    }

    private Observable<String> startGetAccessToken(final String str, final String str2, final String str3) {
        return QvLocationManager.getInstance().getCurrentService(4).flatMap(new Function() { // from class: com.quvii.qvweb.oauth.a
            @Override // io.reactivex.functions.Function
            public final Object apply(Object obj) {
                return this.f9287a.lambda$startGetAccessToken$3(str, str2, str3, (String) obj);
            }
        });
```

## E035 oauth_retry

Single retry and 403 handling.

Source: `com/quvii/qvweb/oauth/QvOAuthManager.java:290-320`

```java
    public Observable<String> getAccessToken(final String str, String str2, String str3) {
        return startGetAccessToken(str, str2, str3).subscribeOn(Schedulers.io()).retry(new Predicate<Throwable>() { // from class: com.quvii.qvweb.oauth.QvOAuthManager.2
            int retryCount = 0;

            @Override // io.reactivex.functions.Predicate
            public boolean test(Throwable th) {
                int i2 = this.retryCount + 1;
                this.retryCount = i2;
                if (i2 > 1) {
                    QvOAuthManager.this.mRefreshing.set(false);
                    return false;
                }
                if (!TextUtils.isEmpty(QvOAuthSpUtil.getInstance().getAuthAccessToken(str))) {
                    QvOAuthManager.this.clearToken(str);
                    LogUtil.i("clear cache, retry");
                    return true;
                }
                if ((th instanceof HttpException) && ((HttpException) th).code() == 403) {
                    QvOAuthManager.this.mRefreshing.set(false);
                    return false;
                }
                QvLocationManager.getInstance().researchCurrentService(4);
                QvOAuthManager.this.mRefreshing.set(false);
                return false;
            }
        }).map(new Function() { // from class: com.quvii.qvweb.oauth.f
            @Override // io.reactivex.functions.Function
            public final Object apply(Object obj) {
                return this.f9301a.lambda$getAccessToken$0((String) obj);
            }
        });
```

## E036 oauth_storage

Tokens encrypted in the oauth SharedPreferences.

Source: `com/quvii/qvweb/oauth/QvOAuthSpUtil.java:23-47`

```java
    public void clearAccountInfo(String str) {
        SharedPreferences.Editor editor = getEditor();
        editor.remove(KEY_AUTH_ACCESS_TOKEN + str);
        editor.remove(KEY_AUTH_REFRESH_TOKEN + str);
        editor.commit();
    }

    public String getAuthAccessToken(String str) {
        return getEncryptValue(KEY_AUTH_ACCESS_TOKEN + str);
    }

    public String getAuthRefreshToken(String str) {
        return getEncryptValue(KEY_AUTH_REFRESH_TOKEN + str);
    }

    @Override // com.quvii.qvweb.publico.utils.QvSpUtil
    protected String getSharePreferencesName() {
        return "oauth";
    }

    public void setAccountInfo(String str, String str2, String str3, String str4, String str5) {
        SharedPreferences.Editor editor = getEditor();
        editor.putString(KEY_AUTH_ACCESS_TOKEN + str, EncryptUtil.encodeWithAes(str4));
        editor.putString(KEY_AUTH_REFRESH_TOKEN + str, EncryptUtil.encodeWithAes(str5));
        editor.commit();
```

## E037 mqtt_connect

MQTT client, SSL, topics and QoS.

Source: `com/quvii/shadow/mqtt/QvMqttManager.java:170-245`

```java
    private final void initMqtt(String str) {
        getMClient().setClientId(getMqttClientId());
        getMClient().setSslSocketFactory(d.f(str, "ssl", false, 2, null) ? OkHttpUtil.getSslSocketFactory() : null);
        getMClient().setServiceUrl(str);
        getMClient().setConnectStatusListener(new QvMqttClient.ConnectStatusListener() { // from class: com.quvii.shadow.mqtt.QvMqttManager.initMqtt.1
            @Override // com.quvii.shadow.mqtt.QvMqttClient.ConnectStatusListener
            public void onConnectComplete() {
                QvMqttManager.currentWaitTime = 0;
                QvMqttManager qvMqttManager = QvMqttManager.INSTANCE;
                qvMqttManager.startMqttBeat();
                j1.a onMqttConnectSuccessListener2 = qvMqttManager.getOnMqttConnectSuccessListener();
                if (onMqttConnectSuccessListener2 != null) {
                    onMqttConnectSuccessListener2.invoke();
                }
            }

            @Override // com.quvii.shadow.mqtt.QvMqttClient.ConnectStatusListener
            public void onConnectFail(int i2) {
                QvObservable qvObservable = QvMqttManager.observableMqttBeat;
                if (qvObservable != null) {
                    qvObservable.stop();
                }
                if (i2 != -10034) {
                    if (QvMqttManager.currentWaitTime <= 10) {
                        QvMqttManager.INSTANCE.setFailReconnect();
                        return;
                    } else {
                        QvMqttManager.INSTANCE.researchP2PLocal();
                        QvMqttManager.currentWaitTime = 0;
                        return;
                    }
                }
                QvOAuthManager.getInstance().clearCurrentToken();
                QvMqttManager.currentWaitTime = 0;
                QvMqttManager qvMqttManager = QvMqttManager.INSTANCE;
                if (qvMqttManager.isRun()) {
                    qvMqttManager.realConnect(5);
                }
            }

            @Override // com.quvii.shadow.mqtt.QvMqttClient.ConnectStatusListener
            public void onConnectLost() {
                long jCurrentTimeMillis = System.currentTimeMillis();
                if (jCurrentTimeMillis - QvMqttManager.lastLostRestTime >= QvMqttManager.LAST_RESET_TIME) {
                    QvMqttManager.lastLostRestTime = jCurrentTimeMillis;
                    QvMqttManager.currentLostWaitTime = 0;
                }
                QvObservable qvObservable = QvMqttManager.observableMqttBeat;
                if (qvObservable != null) {
                    qvObservable.stop();
                }
                QvMqttManager.INSTANCE.setLostReconnect();
            }
        });
        getMClient().setMessageListener(new QvMqttClient.MessageListener() { // from class: com.quvii.shadow.mqtt.QvMqttManager.initMqtt.2
            @Override // com.quvii.shadow.mqtt.QvMqttClient.MessageListener
            public void onMessageArrived(String topic, String payload) {
                j.e(topic, "topic");
                j.e(payload, "payload");
                QvMqttManager.INSTANCE.handleMessage(topic, payload);
            }

            @Override // com.quvii.shadow.mqtt.QvMqttClient.MessageListener
            public void onMessageSend(int i2) {
            }
        });
        getMClient().init();
        getMClient().subscribe(QvShadowConfig.MQTT_SUBSCRIBE_PATH_CUSTOM + SDKVariates.ACCOUNT_ID + '/' + SDKVariates.ALARM_CLIENT_ID + "/#", 1);
        QvMqttClient mClient = getMClient();
        StringBuilder sb = new StringBuilder();
        sb.append(QvShadowConfig.MQTT_SUBSCRIBE_PATH_SYSTEM);
        sb.append(SDKVariates.ALARM_CLIENT_ID);
        sb.append("/#");
        mClient.subscribe(sb.toString(), 1);
        connect();
    }
```

## E038 mqtt_retry

OAuth as password and reconnection.

Source: `com/quvii/shadow/mqtt/QvMqttManager.java:248-297`

```java
    public final void realConnect(int i2) {
        LogUtil.i("realConnect: " + i2);
        realDisconnect();
        final QvObservable qvObservable = new QvObservable();
        Observable.timer((long) i2, TimeUnit.SECONDS).flatMap(new Function() { // from class: com.quvii.shadow.mqtt.a
            @Override // io.reactivex.functions.Function
            public final Object apply(Object obj) {
                return QvMqttManager.m30realConnect$lambda1(qvObservable, (Long) obj);
            }
        }).subscribe(new Observer<String>() { // from class: com.quvii.shadow.mqtt.QvMqttManager.realConnect.2
            @Override // io.reactivex.Observer
            public void onComplete() {
            }

            @Override // io.reactivex.Observer
            public void onError(Throwable e2) {
                j.e(e2, "e");
                LogUtil.printStackTrace(e2);
                QvMqttManager.INSTANCE.setFailReconnect();
            }

            @Override // io.reactivex.Observer
            public void onSubscribe(Disposable d2) {
                j.e(d2, "d");
                qvObservable.setDisposable(d2);
            }

            @Override // io.reactivex.Observer
            public void onNext(String s2) {
                j.e(s2, "s");
                if (qvObservable.isStop()) {
                    LogUtil.i("stop");
                    return;
                }
                QvMqttManager qvMqttManager = QvMqttManager.INSTANCE;
                qvMqttManager.getMClient().setUserName(qvMqttManager.getMqttClientId());
                qvMqttManager.getMClient().setPassword(s2);
                qvMqttManager.getMClient().connect();
            }
        });
        observableConnect = qvObservable;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: renamed from: realConnect$lambda-1, reason: not valid java name */
    public static final ObservableSource m30realConnect$lambda1(QvObservable observable, Long it) {
        j.e(observable, "$observable");
        j.e(it, "it");
        return !observable.isStop() ? QvOAuthManager.getInstance().getAccessToken() : Observable.just("");
    }
```

## E039 mqtt_backoff

Bounded incremental wait.

Source: `com/quvii/shadow/mqtt/QvMqttManager.java:329-343`

```java
    /* JADX INFO: Access modifiers changed from: private */
    public final void setFailReconnect() {
        if (isRun) {
            realConnect(currentWaitTime);
            currentWaitTime = m1.d.b(10, currentWaitTime + 1);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void setLostReconnect() {
        if (isRun) {
            realConnect(currentLostWaitTime);
            currentLostWaitTime = m1.d.b(10, currentLostWaitTime + 1);
        }
    }
```

## E040 mqtt_scheme

mqtts ssl or mqtt tcp derivation.

Source: `com/quvii/shadow/mqtt/QvMqttManager.java:416-438`

```java
    public final void initMqttClient() {
        StringBuilder sb;
        unInitMqtt();
        LogUtil.i("init Mqtt Client");
        String ustAddress = QvLocationManager.getInstance().getCurrentUrl(2);
        try {
            j.d(ustAddress, "ustAddress");
            String strSubstring = ustAddress.substring(d.o(ustAddress, "://", 0, false, 6, null) + 3, d.i(ustAddress));
            j.d(strSubstring, "this as java.lang.String…ing(startIndex, endIndex)");
            if (d.f(ustAddress, "mqtts", false, 2, null)) {
                sb = new StringBuilder();
                sb.append("ssl://");
                sb.append(strSubstring);
            } else {
                sb = new StringBuilder();
                sb.append("tcp://");
                sb.append(strSubstring);
            }
            initMqtt(sb.toString());
        } catch (Exception e2) {
            researchP2PLocal();
            LogUtil.printStackTrace(e2);
        }
```

## E041 shadow_api

HTTP routes and shadow topics.

Source: `com/quvii/shadow/QvShadowConfig.java:4-18`

```java
public final class QvShadowConfig {
    public static final String COMMAND_MQTT_APP_BEAT = "app_beat";
    public static final String COMMAND_MQTT_DEVICE_INFO_UPDATE = "device_info_update";
    public static final String COMMAND_MQTT_DEVICE_STATUS_UPDATE = "device_status_update";
    public static final String COMMAND_MQTT_ENTRANCE_DELETE = "entrance_device_info_delete";
    public static final String COMMAND_MQTT_USER_HOUSE_UNBINDING = "user_house_unbinding";
    public static final boolean DEBUG_MODE = false;
    public static final String HTTP_PATH_GET_SHADOW_STATUS = "/openapi-tdk/device/status";
    public static final String HTTP_PATH_GET_SHADOW_STATUS_MULTI = "/openapi-tdk/device/status/multi";
    public static final String HTTP_PATH_SUBSCRIBE = "/openapi-tdk/device/status/sub";
    public static final String HTTP_PATH_UNSUBSCRIBE = "/openapi-tdk/device/status/unsub";
    public static final QvShadowConfig INSTANCE = new QvShadowConfig();
    public static final String MQTT_PUBLISH_PATH_BEAT = "tdk/client_api/sys/update/";
    public static final String MQTT_SUBSCRIBE_PATH_CUSTOM = "tdk/client_api/users/get/";
    public static final String MQTT_SUBSCRIBE_PATH_SYSTEM = "tdk/client_api/sys/get/";
```

## E042 p2p_native

Creation of the native P2P client.

Source: `com/quvii/p2pv2/QvP2PClientV2.java:249-256`

```java
    @Override // com.quvii.p2p.QvP2PManager
    protected void initSDK() {
        LogUtil.i("p2p version: v3.2");
        QvP2PV2Api.setDefaultServiceMask(getAllMask());
        int p2pMqttKeepAliveSec = SpUtil.getInstance().getP2pMqttKeepAliveSec();
        LogUtil.i("p2p client id:" + getClientId());
        QvP2PV2Api.createP2PClient(createServiceUrl(), getUstAddress(), getUstTestAddress(), getCaPath(), getKeyPath(), getCertPath(), getOem(), getClientId(), getAppType(), getAppId(), isForceTrans() ? 1 : 0, p2pMqttKeepAliveSec);
        LogUtil.i("p2p client id finish");
```

## E043 prefs_crypto

Preferences encrypted with AES.

Source: `com/quvii/ubell/publico/util/SpUtil.java:61-91`

```java
    private String getEncryptValue(String str) {
        return QvEncryptKeyStoreUtil.decodeWithAes(getValue(str));
    }

    public static SpUtil getInstance() {
        return getInstance(QvBaseApp.getInstance());
    }

    private int getIntValue(String str) {
        return getSP().getInt(str, -1);
    }

    private Long getLongValue(String str) {
        return Long.valueOf(getSP().getLong(str, -1L));
    }

    private SharedPreferences getSP() {
        if (this.sp == null) {
            this.sp = this.context.getSharedPreferences(this.SP_NAME, 0);
        }
        return this.sp;
    }

    private void setBooleanValue(String str, boolean z2) {
        getEditor().putBoolean(str, z2);
        getEditor().commit();
    }

    private void setEncryptValue(String str, String str2) {
        setValue(str, QvEncryptKeyStoreUtil.encodeWithAes(str2));
    }
```

## E044 prefs_passwords

WiFi credentials and saving by default.

Source: `com/quvii/ubell/publico/util/SpUtil.java:180-214`

```java
    public String getPassword() {
        return getEncryptValue("password");
    }

    public boolean getShowConnectType() {
        return getBooleanValue(APP_DEBUG_SHOW_CONNECT_TYPE);
    }

    public String getSsidInfo(String str) {
        return QvEncryptKeyStoreUtil.decodeWithAes(getValue(QvEncryptKeyStoreUtil.encodeWithAes(SSID_INFO + str)));
    }

    public String getUserName() {
        return getEncryptValue("username");
    }

    public String getValue(String str) {
        return getSP().getString(str, "");
    }

    public boolean getVibration() {
        return getBooleanValue(VIBRATION, false);
    }

    public boolean isAppAutoRotate() {
        return getSP().getBoolean(APP_IS_AUTO_ROTATE, true);
    }

    public boolean isAppDebugMode() {
        return getSP().getBoolean(APP_DEBUG_MODE, AppConfig.debug);
    }

    public boolean isAppIsSavePassword() {
        return getSP().getBoolean(APP_IS_SAVE_PASSWORD, true);
    }
```

## E045 sqlcipher

SQLCipher key and migration.

Source: `com/quvii/ubell/publico/database/SQLCipherHelperImpl.java:15-36`

```java
    public SQLCipherHelperImpl(DatabaseDefinition databaseDefinition, DatabaseHelperListener databaseHelperListener) {
        super(databaseDefinition, databaseHelperListener, new SQLiteDatabaseHook() { // from class: com.quvii.ubell.publico.database.SQLCipherHelperImpl.1
            @Override // net.sqlcipher.database.SQLiteDatabaseHook
            public void postKey(SQLiteDatabase sQLiteDatabase) {
                if (SpUtil.getInstance().getDBVersion() == 1) {
                    return;
                }
                SpUtil.getInstance().setDBVersion(1);
                LogUtil.i("SQLCipherHelperImpl  postKey");
                sQLiteDatabase.rawExecSQL("PRAGMA cipher_migrate;");
            }

            @Override // net.sqlcipher.database.SQLiteDatabaseHook
            public void preKey(SQLiteDatabase sQLiteDatabase) {
            }
        });
    }

    @Override // com.quvii.ubell.publico.database.MySQLCipherOpenHelper
    protected String getCipherSecret() {
        return new String(QvEncryptKeyStoreUtil.get());
    }
```

## E046 keystore

AES 128 generation.

Source: `com/quvii/qvlib/util/QvEncryptKeyStoreUtil.java:240-251`

```java
    private static byte[] createAesKey() {
        SecureRandom secureRandom;
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            int i2 = Build.VERSION.SDK_INT;
            if (i2 >= 28) {
                secureRandom = SecureRandom.getInstance(SHA1PRNG);
            } else {
                secureRandom = i2 > 23 ? SecureRandom.getInstance(SHA1PRNG, new CryptoProvider()) : SecureRandom.getInstance(SHA1PRNG, "Crypto");
            }
            keyGenerator.init(128, secureRandom);
            return keyGenerator.generateKey().getEncoded();
```

## E047 keystore_init

AES key protected with RSA AndroidKeyStore.

Source: `com/quvii/qvlib/util/QvEncryptKeyStoreUtil.java:452-498`

```java
    private static void initAesKey() {
        LogUtil.i("init aes key");
        if (QvSpHelper.getInstance().getLocalAesKey().equals("0") || TextUtils.isEmpty(QvSpHelper.getInstance().getLocalAesKey())) {
            byte[] bArrCreateAesKey = createAesKey();
            if (QvLibConfig.IS_SHOW_ENCRYPT_LOG) {
                LogUtil.i("set aes key:" + Arrays.toString(bArrCreateAesKey));
            }
            QvSpHelper.getInstance().setLocalAesKey(encryptWithRsa(QvBase64.encode(bArrCreateAesKey)));
        }
        temp = QvBase64.decode(decryptWithRsa(QvSpHelper.getInstance().getLocalAesKey()));
        if (QvLibConfig.IS_SHOW_ENCRYPT_LOG) {
            LogUtil.i("get aes key:" + Arrays.toString(temp));
        }
        secretKeySpec = new SecretKeySpec(temp, "AES");
    }

    private static void initKey() {
        try {
            initAesKey();
        } catch (Exception e2) {
            LogUtil.printStackTrace(e2);
        }
    }

    public static void initKeyStore() {
        try {
            KeyStore keyStore2 = KeyStore.getInstance("AndroidKeyStore");
            keyStore = keyStore2;
            keyStore2.load(null);
        } catch (Exception e2) {
            LogUtil.printStackTrace(e2);
        }
        int i2 = Build.VERSION.SDK_INT;
        createNewKeys(KEY_STONE_NAME_NEW);
        if (i2 < 28) {
            try {
                privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(KEY_STONE_NAME_NEW, null);
                if (QvLibConfig.IS_SHOW_ENCRYPT_LOG) {
                    LogUtil.i("init aes key" + privateKeyEntry.toString());
                }
            } catch (Exception e3) {
                LogUtil.printStackTrace(e3);
            }
        }
        checkNewKey();
        initKey();
    }
```

## E048 crypto_hashes

Device SHA-256 and HMAC-SHA1.

Source: `com/quvii/publico/utils/QvEncrypt.java:15-26`

```java
    private static final String MAC_NAME = "HmacSHA1";

    public static String EncodeDevicePassword(String str) {
        return (str == null || str.length() < 64) ? sha256(str) : str;
    }

    public static String HmacSHA1Encrypt(String str, String str2) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(str2.getBytes("UTF-8"), MAC_NAME);
        Mac mac = Mac.getInstance(MAC_NAME);
        mac.init(secretKeySpec);
        return bytesToHex(mac.doFinal(str.getBytes("UTF-8")));
    }
```

## E049 update_tls

Updater TrustManager.

Source: `com/quvii/ubell/publico/version/AppVersionManager.java:101-126`

```java
    private SSLSocketFactory getSSLSocketFactory() {
        try {
            SSLContext sSLContext = SSLContext.getInstance("SSL");
            sSLContext.init(null, getTrustManager(), new SecureRandom());
            return sSLContext.getSocketFactory();
        } catch (Exception e2) {
            log(e2.getMessage());
            return null;
        }
    }

    private static TrustManager[] getTrustManager() {
        return new TrustManager[]{new X509TrustManager() { // from class: com.quvii.ubell.publico.version.AppVersionManager.2
            @Override // javax.net.ssl.X509TrustManager
            public void checkClientTrusted(X509Certificate[] x509CertificateArr, String str) throws CertificateException {
            }

            @Override // javax.net.ssl.X509TrustManager
            public void checkServerTrusted(X509Certificate[] x509CertificateArr, String str) {
            }

            @Override // javax.net.ssl.X509TrustManager
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
```

## E050 update_endpoint

Update endpoint, headers and timeout.

Source: `com/quvii/ubell/publico/version/AppVersionManager.java:272-313`

```java
    public void getVersion() {
        log("getVersion");
        String str = this.version;
        if (str != null && !str.equals(getCurrent())) {
            log("clean version info: " + this.version + " " + getCurrent());
            saveInfo("");
        }
        saveCurrent(this.version);
        if (TextUtils.isEmpty(this.serviceAddress) || this.appId < 1 || TextUtils.isEmpty(this.version)) {
            return;
        }
        if (System.currentTimeMillis() - getQueryTime() < this.queryInterval) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(QvTimeConstants.DATE_FORMAT_DEFAULT, Locale.ENGLISH);
            log("time filter: last time = " + simpleDateFormat.format(new Date(getQueryTime())) + " current time = " + simpleDateFormat.format(new Date(System.currentTimeMillis())));
            return;
        }
        String str2 = "https://" + this.serviceAddress + ":" + this.servicePort + "/webs/version/app/version_check.jsp?os=android&appid=" + this.appId + "&version=" + this.version + "&clientid=" + SDKVariates.ALARM_CLIENT_ID;
        log(str2);
        if (this.okHttpClient == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            TimeUnit timeUnit = TimeUnit.SECONDS;
            this.okHttpClient = builder.connectTimeout(10L, timeUnit).readTimeout(10L, timeUnit).writeTimeout(10L, timeUnit).hostnameVerifier(new AllowAllHostnameVerifier()).sslSocketFactory(getSSLSocketFactory()).build();
        }
        this.okHttpClient.newCall(new Request.Builder().url(str2).addHeader("Accept-Language", localeToBcp47Language(Locale.getDefault())).build()).enqueue(new Callback() { // from class: com.quvii.ubell.publico.version.AppVersionManager.1
            @Override // okhttp3.Callback
            public void onFailure(Call call, IOException iOException) {
                AppVersionManager.this.log(iOException.getMessage());
            }

            @Override // okhttp3.Callback
            public void onResponse(Call call, Response response) throws IOException {
                ResponseBody responseBodyBody;
                AppVersionManager.this.log("response code : " + response.code());
                if (response.code() != 200 || (responseBodyBody = response.body()) == null) {
                    return;
                }
                String strString = responseBodyBody.string();
                AppVersionManager.this.log("response: " + strString);
                AppVersionManager.this.setQueryTime(System.currentTimeMillis());
                AppVersionManager.this.saveInfo(strString);
            }
        });
```

## E051 alarm_pagination

Alarm query in pages of 15.

Source: `com/quvii/ubell/main/model/MainMessageListModel.java:85-99`

```java
    public /* synthetic */ ObservableSource lambda$getMessageList$0(String str, int i2, Integer num) throws Exception {
        return QvAlarmCore.getInstance().queryAlarmInfo(str, i2, 15, this.msgFilter.getFilteredDevId(), (String) null, (String) null, this.msgFilter.getFilteredEvent(), this.msgFilter.getFilteredDate());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ List lambda$getMessageList$1(QvAlarmMsg qvAlarmMsg) throws Exception {
        ArrayList arrayList = new ArrayList(qvAlarmMsg.getList() != null ? qvAlarmMsg.getList().size() : 0);
        String maxId = qvAlarmMsg.getMaxId();
        this.mMaxId = maxId;
        if (maxId.equals("0")) {
            LogUtil.i("alarm list is null");
        } else {
            for (QvAlarmMsgItem qvAlarmMsgItem : qvAlarmMsg.getList()) {
                arrayList.add(new AlarmInfo(qvAlarmMsgItem.getId(), qvAlarmMsgItem.getAlarmId(), qvAlarmMsgItem.getDevId(), qvAlarmMsgItem.getChNum(), qvAlarmMsgItem.getChName(), qvAlarmMsgItem.getEvent(), qvAlarmMsgItem.getTime(), qvAlarmMsgItem.getAlarmState(), qvAlarmMsgItem.getAlarmInfo(), qvAlarmMsgItem.getMsgState(), qvAlarmMsgItem.getMsgSaveType(), Integer.parseInt(qvAlarmMsgItem.getTime().split(" ")[0].replace(Operator.Operation.MINUS, "")), qvAlarmMsgItem.getSensorChannel(), qvAlarmMsgItem.getSensorId(), qvAlarmMsgItem.getSensorName()));
            }
```

## E052 alarm_errors

Event query errors.

Source: `com/quvii/ubell/main/model/MainMessageListModel.java:206-253`

```java
    public void getMessageList(final String str, final int i2, final MainMessageListContract.OnGetMessageListener onGetMessageListener) {
        UpdateAlarmState().flatMap(new Function() { // from class: com.quvii.ubell.main.model.n
            @Override // io.reactivex.functions.Function
            public final Object apply(Object obj) {
                return this.f9583a.lambda$getMessageList$0(str, i2, (Integer) obj);
            }
        }).subscribeOn(Schedulers.io()).observeOn(Schedulers.io()).map(new Function() { // from class: com.quvii.ubell.main.model.o
            @Override // io.reactivex.functions.Function
            public final Object apply(Object obj) {
                return this.f9586a.lambda$getMessageList$1((QvAlarmMsg) obj);
            }
        }).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<List<AlarmInfo>>() { // from class: com.quvii.ubell.main.model.MainMessageListModel.1
            @Override // io.reactivex.Observer
            public void onComplete() {
            }

            @Override // io.reactivex.Observer
            public void onError(Throwable th) {
                Integer numValueOf;
                LogUtil.printStackTrace(th);
                String message = th.getMessage();
                LogUtil.i("queryAlarmInfo fail, " + message);
                if (message.contains("org.simpleframework.xml.core.ElementException")) {
                    onGetMessageListener.onReceive(new ArrayList(), "0");
                } else {
                    if (message.contains("HTTP 502 Bad Gateway")) {
                        onGetMessageListener.onError(Integer.valueOf(RetryWithTime.BAD_GATEWAY));
                        return;
                    }
                    try {
                        numValueOf = Integer.valueOf(Integer.parseInt(message));
                    } catch (Exception unused) {
                        LogUtil.i("");
                        numValueOf = null;
                    }
                    onGetMessageListener.onError(numValueOf);
                }
            }

            @Override // io.reactivex.Observer
            public void onSubscribe(Disposable disposable) {
            }

            @Override // io.reactivex.Observer
            public void onNext(List<AlarmInfo> list) {
                onGetMessageListener.onReceive(list, MainMessageListModel.this.mMaxId);
            }
        });
```

## E053 main_tabs

Navigation and icons of four tabs.

Source: `com/quvii/ubell/main/view/MainTabActivity.java:161-211`

```java
    private void switchFragment(BaseFragment baseFragment, int i2) {
        switchMenu(i2);
        this.currentFragmentNum = i2;
        if (this.currentFragment == baseFragment && baseFragment.isVisible()) {
            return;
        }
        androidx.fragment.app.q qVarL = getSupportFragmentManager().l();
        BaseFragment baseFragment2 = this.currentFragment;
        if (baseFragment2 != null) {
            qVarL.n(baseFragment2);
        }
        if (baseFragment.isAdded()) {
            qVarL.t(baseFragment);
        } else {
            qVarL.b(R.id.main_content, baseFragment, baseFragment.getClass().getName());
        }
        qVarL.g();
        this.currentFragment = baseFragment;
    }

    private void switchMenu(int i2) {
        setDefaultView();
        if (i2 == 1) {
            Drawable drawable = getResources().getDrawable(R.drawable.main_btn_device_select);
            drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
            ((ActivityMainTabBinding) this.binding).mainIvDevice.setCompoundDrawables(null, drawable, null, null);
            ((ActivityMainTabBinding) this.binding).mainIvDevice.setTextColor(getResources().getColor(R.color.color_bottom_tab_text));
            return;
        }
        if (i2 == 2) {
            Drawable drawable2 = getResources().getDrawable(R.drawable.main_btn_call_log_select);
            drawable2.setBounds(0, 0, drawable2.getMinimumWidth(), drawable2.getMinimumHeight());
            ((ActivityMainTabBinding) this.binding).mainIvCallLog.setCompoundDrawables(null, drawable2, null, null);
            ((ActivityMainTabBinding) this.binding).mainIvCallLog.setTextColor(getResources().getColor(R.color.color_bottom_tab_text));
            return;
        }
        if (i2 == 3) {
            Drawable drawable3 = getResources().getDrawable(R.drawable.main_btn_file_select);
            drawable3.setBounds(0, 0, drawable3.getMinimumWidth(), drawable3.getMinimumHeight());
            ((ActivityMainTabBinding) this.binding).mainIvFile.setCompoundDrawables(null, drawable3, null, null);
            ((ActivityMainTabBinding) this.binding).mainIvFile.setTextColor(getResources().getColor(R.color.color_bottom_tab_text));
            return;
        }
        if (i2 != 4) {
            return;
        }
        Drawable drawable4 = getResources().getDrawable(R.drawable.main_btn_setting_select);
        drawable4.setBounds(0, 0, drawable4.getMinimumWidth(), drawable4.getMinimumHeight());
        ((ActivityMainTabBinding) this.binding).mainIvMe.setCompoundDrawables(null, drawable4, null, null);
        ((ActivityMainTabBinding) this.binding).mainIvMe.setTextColor(getResources().getColor(R.color.color_bottom_tab_text));
    }
```

## E054 device_empty

Empty view, list and device actions.

Source: `com/quvii/ubell/main/view/MainDeviceListFragment.java:149-199`

```java
    @Override // com.quvii.ubell.main.contract.MainDeviceListContract.View
    public void showCenterAddView() {
        ((FragmentDeviceListBinding) this.binding).rlAddDevice.setVisibility(0);
    }

    @Override // com.quvii.ubell.main.contract.MainDeviceListContract.View
    public void showCustomOrderState(boolean z2) {
        hideLoading();
        s1.c.c().k(new MessageToast(getString(z2 ? R.string.key_command_send_success : R.string.key_command_send_fail)));
    }

    @Override // com.quvii.ubell.main.contract.MainDeviceListContract.View
    public void showDeviceList(List<Device> list) {
        if (list == null || list.size() == 0 || !this.isOnFront) {
            return;
        }
        DeviceAdapter deviceAdapter = this.mAdapter;
        if (deviceAdapter == null) {
            DeviceAdapter deviceAdapter2 = new DeviceAdapter(getActivity(), list, new DeviceAdapter.OnViewClick() { // from class: com.quvii.ubell.main.view.MainDeviceListFragment.1
                @Override // com.quvii.ubell.main.adapter.DeviceAdapter.OnViewClick
                public void onCallGuardClick(Device device) {
                    ((MainDeviceListContract.Presenter) MainDeviceListFragment.this.getP()).callGuard(device);
                }

                @Override // com.quvii.ubell.main.adapter.DeviceAdapter.OnViewClick
                public void onF1Click(Device device) {
                    ((MainDeviceListContract.Presenter) MainDeviceListFragment.this.getP()).checkDeviceF1(device);
                }

                @Override // com.quvii.ubell.main.adapter.DeviceAdapter.OnViewClick
                public void onLock1Click(Device device) {
                    ((MainDeviceListContract.Presenter) MainDeviceListFragment.this.getP()).checkDeviceUnlock(device);
                }

                @Override // com.quvii.ubell.main.adapter.DeviceAdapter.OnViewClick
                public void onPreviewClick(Device device) {
                    MainDeviceListFragment.this.startTargetActivity(device, PreviewActivity.class);
                }

                @Override // com.quvii.ubell.main.adapter.DeviceAdapter.OnViewClick
                public void onSettingClick(Device device) {
                    MainDeviceListFragment.this.startTargetActivity(device, DMConfigActivity.class);
                }
            });
            this.mAdapter = deviceAdapter2;
            ((FragmentDeviceListBinding) this.binding).mainDeviceLanListView.setAdapter((ListAdapter) deviceAdapter2);
        } else {
            deviceAdapter.notifyDataSetChanged();
        }
        ((FragmentDeviceListBinding) this.binding).mainDeviceSrl.setVisibility(0);
    }
```

## E055 shared_config

Settings restrictions for shared device.

Source: `com/quvii/ubell/device/manage/view/DMConfigActivity.java:223-235`

```java
        if (this.device.isShareDevice()) {
            ((ActivityDmConfigBinding) this.binding).tvModifyUnlockPassword.setVisibility(8);
            ((ActivityDmConfigBinding) this.binding).tvModifyPassword.setVisibility(8);
            ((ActivityDmConfigBinding) this.binding).llConfigWifi.setVisibility(8);
            ((ActivityDmConfigBinding) this.binding).rlMoveAlert.setVisibility(8);
            ((ActivityDmConfigBinding) this.binding).rlRingAlert.setVisibility(8);
            ((ActivityDmConfigBinding) this.binding).btSynTime.setVisibility(8);
            ((ActivityDmConfigBinding) this.binding).llUpgrade.setVisibility(8);
            ((ActivityDmConfigBinding) this.binding).tvUser.setVisibility(8);
            ((ActivityDmConfigBinding) this.binding).llF1Time.setVisibility(8);
        }
        ((DMConfigContract.Presenter) getP()).setDevice(this.device);
    }
```

## E056 fcm_payload

FCM payload and token.

Source: `com/quvii/ubell/push/fcm/MyFirebaseMessagingService.java:13-55`

```java
public class MyFirebaseMessagingService extends FirebaseMessagingService {
    @Override // com.google.firebase.messaging.FirebaseMessagingService
    public void onMessageReceived(RemoteMessage remoteMessage) {
        int i2;
        AlarmMessageInfo alarmMessageInfo;
        super.onMessageReceived(remoteMessage);
        LogUtil.i("Receive message ID: " + remoteMessage.getMessageId());
        if (remoteMessage.getData().size() > 0) {
            LogUtil.i("Receive data Message data payload: " + remoteMessage.getData());
        }
        if (remoteMessage.getNotification() != null) {
            LogUtil.i("Receive Message Notification Body: " + remoteMessage.getNotification().getBody());
        }
        Map<String, String> data = remoteMessage.getData();
        String str = data.get("SensorChNo");
        if (TextUtils.isEmpty(str)) {
            i2 = -1;
        } else {
            try {
                i2 = Integer.parseInt(str);
            } catch (Exception unused) {
                LogUtil.i("Integer.parseInt fail: " + str);
                i2 = -1;
            }
        }
        try {
            alarmMessageInfo = new AlarmMessageInfo(data.get("AlarmId"), data.get("DevUmid"), i2, data.get("CameraName"), Integer.parseInt(data.get("NotReadCount")), Integer.parseInt(data.get("AlarmEvent")), data.get("AlarmTime"), 1);
        } catch (Exception e2) {
            LogUtil.printStackTrace(e2);
            alarmMessageInfo = null;
        }
        if (alarmMessageInfo == null) {
            return;
        }
        c.c().k(alarmMessageInfo);
    }

    @Override // com.google.firebase.messaging.FirebaseMessagingService
    public void onNewToken(String str) {
        super.onNewToken(str);
        LogUtil.i("onNewToken: " + str);
        PushHelper.PushModeInit(1, str);
    }
```

## E057 push_flow

Push, FCM and registration events.

Source: `com/quvii/ubell/push/PushHelper.java:55-119`

```java
    public static void HandlePush(AlarmMessageInfo alarmMessageInfo, Context context) {
        LogUtil.i("HandlePush: " + alarmMessageInfo.toString());
        AppVariates.isTalkingBeforePush = PreviewPresenter.isTalkSending;
        int alarmEvent = alarmMessageInfo.getAlarmEvent();
        if (alarmEvent == 2) {
            alarm(context, alarmMessageInfo);
            return;
        }
        if (alarmEvent == 19) {
            if (isProcessCalling) {
                LogUtil.i("process calling...");
                return;
            } else {
                isProcessCalling = true;
                startCallActivity(context, alarmMessageInfo);
                return;
            }
        }
        if (alarmEvent != 20) {
            return;
        }
        stopPushService(context);
        if (SystemUtil.IsAndroidQ() && QvBaseApp.isBackground()) {
            alarm(context, alarmMessageInfo);
        }
    }

    public static void InitPush(Context context) {
        LogUtil.i("InitPush: current mode = " + SpUtil.getInstance().getAppPushMode() + "\ncurrent token = " + SpUtil.getInstance().getAppPushToken());
        try {
            if (!FirebaseMessaging.getInstance().isAutoInitEnabled()) {
                FirebaseMessaging.getInstance().setAutoInitEnabled(true);
            }
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener() { // from class: z0.j
                @Override // com.google.android.gms.tasks.OnCompleteListener
                public final void onComplete(Task task) {
                    PushHelper.lambda$InitPush$0(task);
                }
            });
        } catch (Exception e2) {
            LogUtil.printStackTrace(e2);
        }
    }

    public static void PushModeInit(int i2, String str) {
        LogUtil.i("PushModeInit: " + i2 + " token: " + str);
        SpUtil.getInstance().getAppPushMode();
        String appPushToken = SpUtil.getInstance().getAppPushToken();
        if (TextUtils.isEmpty(str)) {
            LogUtil.i("token is null");
            return;
        }
        if (str.equals(appPushToken)) {
            LogUtil.i("token equal");
            return;
        }
        if (1 != i2) {
            LogUtil.i("type deny");
            return;
        }
        SpUtil.getInstance().setAppPushMode(i2);
        SpUtil.getInstance().setAppPushToken(str);
        QvAlarmCore.getInstance().setUserFcmPush(i2 == 1);
        QvAlarmCore.getInstance().initCid(str);
    }
```

## E058 push_notifications

Call notification and foreground service.

Source: `com/quvii/ubell/push/PushHelper.java:227-280`

```java
    public static void notifyCall(Context context, Intent intent, String str, String str2, String str3) {
        intent.putExtra(AppConst.INTENT_APP_IN_LOCK, true);
        PendingIntent activityPendingIntent = getActivityPendingIntent(context, intent);
        intent.putExtra(AppConst.INTENT_APP_IN_LOCK, false);
        PendingIntent activityPendingIntent2 = getActivityPendingIntent(context, intent);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
        if (notificationManager == null) {
            LogUtil.e("notifyManager is null!");
            return;
        }
        String str4 = str + "_channel1";
        Uri uri = Uri.parse("android.resource://" + context.getPackageName() + "/raw/ring");
        mCreateNotificationChannel(notificationManager, str4, str, uri);
        pushNotification(context, new v.f(context, str4).Z(R.drawable.ic_icon_app).D(str2).s(true).G(6).c0(uri).C(str3).T(1).v("call").J(activityPendingIntent, true).B(activityPendingIntent2));
    }

    private static void notifyMessage(Context context, Intent intent, String str, String str2, String str3) {
        PendingIntent activityPendingIntent = getActivityPendingIntent(context, intent);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
        if (notificationManager == null) {
            LogUtil.e("notifyManager is null!");
            return;
        }
        String str4 = str + "_channel1";
        Uri defaultUri = RingtoneManager.getDefaultUri(2);
        mCreateNotificationChannel(notificationManager, str4, str, defaultUri);
        notificationManager.notify(notifyId, new v.f(context, str4).Z(R.drawable.ic_icon_app).D(str2).s(true).G(6).c0(defaultUri).C(str3).B(activityPendingIntent).g());
    }

    private static void pushNotification(Context context, v.f fVar) {
        Intent intent = new Intent(context, (Class<?>) PushHelperService.class);
        intent.putExtra(AppConst.PUSH_HELPER_SERVICE_NOTIFICATION, fVar.g());
        intent.putExtra(AppConst.PUSH_HELPER_SERVICE_NOTIFICATION_ID, notifyId);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private static void startCallActivity(Context context, AlarmMessageInfo alarmMessageInfo) {
        AppVariates.curIsCalling = true;
        Class cls = QvBaseApp.getActivityCount() > 0 ? PushCallActivity.class : LoadingActivity.class;
        if (SystemUtil.IsAndroidQ() && QvBaseApp.isBackground()) {
            alarmCall(context, alarmMessageInfo);
        } else {
            Intent intent = new Intent(context, (Class<?>) cls);
            intent.putExtra(AppConst.INTENT_APP_IN_BACKGROUND, QvBaseApp.isBackground());
            intent.putExtra(AlarmMessageInfo.NAME, alarmMessageInfo);
            intent.addFlags(SQLiteDatabase.CREATE_IF_NECESSARY);
            LogUtil.i("start activity");
            context.startActivity(intent);
        }
        isProcessCalling = false;
```

## E059 push_timeout

Auto-reject at 30 seconds.

Source: `com/quvii/ubell/push/presenter/PushCallPresenter.java:33-49`

```java
    public PushCallPresenter(PushCallContract.Model model, PushCallContract.View view) {
        super(model, view);
        this.disposableAutoRefuse = new CompositeDisposable();
        this.needVibration = false;
        c.c().o(this);
        Observable.timer(30L, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new SimpleObserver<Long>() { // from class: com.quvii.ubell.push.presenter.PushCallPresenter.1
            @Override // com.quvii.ubell.publico.base.SimpleObserver, io.reactivex.Observer
            public void onSubscribe(Disposable disposable) {
                PushCallPresenter.this.disposableAutoRefuse.add(disposable);
            }

            @Override // io.reactivex.Observer
            public void onNext(Long l2) {
                LogUtil.i("auto refuse");
                PushCallPresenter.this.refuse();
            }
        });
```

## E060 hidden_settings

Visible settings and hidden options.

Source: `com/quvii/ubell/main/view/MainMeFragment.java:127-183`

```java
    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
        setTitlebar(getString(R.string.key_setting), false);
        ((FragmentMeBinding) this.binding).llAutoRotate.setVisibility(8);
        ((FragmentMeBinding) this.binding).llChannelsSwitchMode.setVisibility(8);
        ((FragmentMeBinding) this.binding).llPreviewTime.setVisibility(8);
    }

    @Override // com.qing.mvpart.base.IActivity
    public void processLogic() {
        ((MainMeContract.Presenter) getP()).getState();
    }

    @Override // com.qing.mvpart.base.IActivity
    public void setListener() {
        ((FragmentMeBinding) this.binding).llSound.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.main.view.z
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9664a.lambda$setListener$0(view);
            }
        });
        ((FragmentMeBinding) this.binding).llVibration.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.main.view.a0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9605a.lambda$setListener$1(view);
            }
        });
        ((FragmentMeBinding) this.binding).llAbout.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.main.view.b0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9610a.lambda$setListener$2(view);
            }
        });
        ((FragmentMeBinding) this.binding).llExit.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.main.view.c0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9613a.lambda$setListener$5(view);
            }
        });
        ((FragmentMeBinding) this.binding).llAutoRotate.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.main.view.d0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9616a.lambda$setListener$6(view);
            }
        });
        ((FragmentMeBinding) this.binding).llChannelsSwitchMode.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.main.view.e0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9619a.lambda$setListener$8(view);
            }
        });
        ((FragmentMeBinding) this.binding).llPreviewTime.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.main.view.f0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9623a.lambda$setListener$9(view);
            }
        });
```

## E061 about_debug

Hidden access to TestInfo.

Source: `com/quvii/ubell/me/view/MeAboutActivity.java:45-56`

```java
    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$1(View view) {
        this.sendFlag--;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$2(View view) {
        if (this.sendFlag == -18) {
            startActivity(TestInfoActivity.class);
        }
        this.sendFlag = 0;
    }
```

## E062 file_provider

FileProvider sharing.

Source: `com/quvii/ubell/main/model/MainFileModel.java:111-151`

```java
    public boolean shareFiles(Activity activity, List<String> list) {
        ArrayList<? extends Parcelable> arrayList = new ArrayList<>();
        String str = null;
        int i2 = 0;
        boolean z2 = true;
        while (i2 < list.size()) {
            File file = new File(list.get(i2));
            String mimeType = Utils.getMimeType(file.getAbsolutePath());
            if (!mimeType.startsWith("image/")) {
                z2 = false;
            }
            arrayList.add(Build.VERSION.SDK_INT >= 24 ? FileProvider.f(QvBaseApp.getInstance(), "com.fermax.wibox.file_provider", file) : Uri.fromFile(file));
            i2++;
            str = mimeType;
        }
        boolean z3 = arrayList.size() > 1;
        Intent intent = new Intent(z3 ? "android.intent.action.SEND_MULTIPLE" : "android.intent.action.SEND");
        if (Build.VERSION.SDK_INT >= 24) {
            intent.addFlags(1);
        }
        if (z3) {
            if (z2) {
                intent.setType(str);
            } else {
                intent.setType("file/*");
            }
            intent.putParcelableArrayListExtra("android.intent.extra.STREAM", arrayList);
        } else {
            intent.setType(str);
            intent.putExtra("android.intent.extra.STREAM", arrayList.get(0));
        }
        if (!Utils.hasApplication(activity, intent)) {
            return false;
        }
        try {
            activity.startActivity(intent);
            return true;
        } catch (Exception e2) {
            LogUtil.e(e2.getMessage());
            return false;
        }
```

## E063 sdk_flags

CGI flags and SDK login.

Source: `com/quvii/publico/common/SDKConfig.java:4-58`

```java
public class SDKConfig {
    public static boolean ACCEPT_SUB_SHARE = false;
    public static boolean AES_INIT_BY_SELF = false;
    public static boolean AUTH_MANAGER_V2 = false;
    public static int AUTH_TIME_OUT = 5;
    public static String CGI_PROTOCOL = "https";
    public static int CONNECT_DEVICE_TIMEOUT = 20;
    public static boolean DEBUG = true;
    public static boolean DEBUG_HTTP = false;
    public static boolean DEVICE_AUDIO_NS_PROCESS = false;
    public static boolean DEV_MODE = false;
    public static boolean DIRECT_SHARE_DEVICE = false;
    public static final String ENCODING = "UTF-8";
    public static boolean IP_ADD_VSU_FORCE_HTTPS = false;
    public static boolean IS_OPEN_AUTH = false;
    public static boolean NEW_ADD_DEVICE_PROTOCOL = false;
    public static boolean OLD_CHANNEL_INFO_PROTOCOL = false;
    public static boolean P2P_LOCAL_MODE = false;
    public static final int PLAYER_PTZ_DEFAULT_PROTOCOL = 2;
    public static final String PROTOCOL = "quii";
    public static final int SEARCH_MEDIA_COUNT_LIMIT = 10240;
    public static boolean SSO_ENABLE = false;
    public static boolean SUB_DEVICE_ALL = false;
    public static boolean SUPPORT_AUTO_LAN_CONNECT = false;
    public static boolean SUPPORT_BLUETOOTH_TALK = true;
    public static boolean SUPPORT_CLOUD_DOORBELL = false;
    public static boolean SUPPORT_DEVICE_SHADOW = false;
    public static boolean SUPPORT_EXPAND_ALARM = false;
    public static boolean SUPPORT_IP_DEVICE_DUPLICATE_IP = false;
    public static boolean SUPPORT_PUSH_TOKEN_V2 = false;
    public static int TALK_CONNECT_TIMEOUT = 8;
    public static String THIRD_PARTY_ACCOUNT_TAG = null;
    public static int VIDEO_CHANNEL_ORDER_TIMEOUT = 8;
    public static int WAIT_AUTH_CONNECT_TIME_OUT = 10;
    public static boolean isAccountLogin = true;
    public static boolean isNotifyEnable = false;
    public static boolean isPadMode = false;
    public static Integer loginMode;

    public static int getLoginMode() {
        Integer num = loginMode;
        if (num != null) {
            return num.intValue();
        }
        return isAccountLogin ? 0 : 1;
    }

    public static boolean isNoLoginMode() {
        return getLoginMode() == 1;
    }

    public static boolean isP2PV2() {
        return SDKVariates.getP2PManager() != null && SDKVariates.getP2PManager().getVersion() == 1;
    }
}
```

## E064 privacy_url

Push mode and HTTP privacy URL.

Source: `com/quvii/ubell/publico/common/AppConst.java:63-70`

```java
    public static final int PUSH_MODE_ALL = 10;
    public static final int PUSH_MODE_FCM = 1;
    public static final int PUSH_MODE_GT = 0;
    public static final int QUERY_STATE_LOAD_MORE = 2;
    public static final int QUERY_STATE_NORMAL = 0;
    public static final int QUERY_STATE_REFRESH = 1;
    public static final String ST_Privacy_Policy_URL = "http://94.101.81.118/privacy/privacy_policy.html";
    public static final String TIME_ZONE = "time_zone";
```

## E065 connectivity_probe

External connectivity probes.

Source: `com/quvii/qvlib/util/QvNetUtil.java:225-240`

```java
    public static void isGoogleNetAvailable(final CallBack callBack) {
        LogUtil.i("check google network");
        if (isCheckGoogle) {
            LogUtil.i("net checking...");
        } else {
            isCheckGoogle = true;
            isNetWorkAvailableOfGet("https://www.apple.com", 5, new CallBack() { // from class: com.quvii.qvlib.util.QvNetUtil.2
                @Override // com.quvii.qvlib.util.QvNetUtil.CallBack
                public void isAvailable(boolean z2) {
                    if (z2) {
                        LogUtil.i("start check google");
                        QvNetUtil.isNetWorkAvailableOfGet("https://www.google.com", 5, new CallBack() { // from class: com.quvii.qvlib.util.QvNetUtil.2.1
                            @Override // com.quvii.qvlib.util.QvNetUtil.CallBack
                            public void isAvailable(boolean z3) {
                                LogUtil.i("check google ret = " + z3);
                                boolean unused = QvNetUtil.isCheckGoogle = false;
```

## E066 start_guide

Resources of the declared guide.

Source: `com/quvii/ubell/main/view/StartGuideActivity.java:18-26`

```java
public class StartGuideActivity extends BaseActivity<ActivityStartGuideBinding, IPresenter> {
    private MyPagerAdapter<View> adapter;
    private Drawable[] mDrawables;
    private String[] mStrDes;
    private List<View> viewDatas;
    private ImageView[] views;
    private static int[] mIntDrawables = {R.drawable.guide_display1, R.drawable.guide_display2, R.drawable.guide_display3};
    private static int[] mIntDes = {R.string.key_guide_more_locks, R.string.key_guide_more_cameras, R.string.key_guide_play_back};

```

## E067 test_advanced

Advanced options gate.

Source: `com/quvii/ubell/test/view/TestInfoActivity.java:92-100`

```java
    public /* synthetic */ void lambda$showAdvanced$11(MyDialog myDialog) {
        myDialog.dismiss();
        if (QvEncrypt.sha256(myDialog.getEditText()).equals("954116d6af3aa3b01783be497a6f61c2366a87da8a80cbbd84b50951b65c1d33")) {
            ((ActivityTestInfoBinding) this.binding).tvAdvanced.setVisibility(8);
            ((ActivityTestInfoBinding) this.binding).llForceForwarding.setVisibility(0);
            ((ActivityTestInfoBinding) this.binding).llShowConnect.setVisibility(0);
            ((ActivityTestInfoBinding) this.binding).llP2pTimeout.setVisibility(0);
        }
    }
```

## E068 device_ports

P2P, CGI and video port mapping.

Source: `com/quvii/ubell/publico/common/DeviceList.java:47-95`

```java
    public static void getDevCgiPort(Device device) {
        if (portVideoState) {
            for (int i2 = 0; i2 < 10; i2++) {
                try {
                    LogUtil.i("wait..." + i2);
                    Thread.sleep(1000L);
                    if (device.getCgiPort() > 0 || !portVideoState) {
                        break;
                    }
                } catch (InterruptedException unused) {
                    return;
                }
            }
        }
        synchronized (lockPort) {
            try {
                portVideoState = true;
                if (device.getCgiPort() < 1) {
                    int iAddPortByP2P = QvJniApi.addPortByP2P(device.getCid(), 1, 0);
                    LogUtil.i("cgi port: " + iAddPortByP2P);
                    device.setCgiPort(iAddPortByP2P);
                }
                portVideoState = false;
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public static synchronized boolean getDevPort(Device device, boolean z2) {
        if (device.getPort() > 0) {
            return true;
        }
        if (portVideoState) {
            LogUtil.e("get port....");
            return false;
        }
        portVideoState = true;
        int iAddPortByP2P = QvJniApi.addPortByP2P(device.getCid(), 0, 0);
        if (z2 && iAddPortByP2P == -1 && DeviceHelper.getInstance().isDirectDeviceStatus(device.getCid())) {
            LogUtil.i("reset port");
            DeviceHelper.CleanPort(device);
            iAddPortByP2P = QvJniApi.addPortByP2P(device.getCid(), 0, 0);
        }
        LogUtil.i("final port = " + iAddPortByP2P);
        device.setPort(iAddPortByP2P);
        portVideoState = false;
        return true;
    }
```

## E069 app_database

Database name and version.

Source: `com/quvii/ubell/publico/database/AppDatabase.java:14-17`

```java
public class AppDatabase {
    public static final String NAME = "database";
    public static final int VERSION = 11;

```

## E070 error_map

Reduced error mapping.

Source: `com/quvii/ubell/publico/common/ErrorCode.java:6-16`

```java
public class ErrorCode {
    public static int getStrId(int i2) {
        if (i2 == -999) {
            return R.string.key_connect_fail;
        }
        if (i2 == 100101112 || i2 == 100152003) {
            return R.string.key_password_incorrect;
        }
        return 0;
    }
}
```

## E071 root_detection

Root detection function declared, with no calls found in com.quvii.ubell.

Source: `com/quvii/ubell/publico/util/Utils.java:245-255`

```java
    public static boolean isRooted() {
        String[] strArr = {"/system/xbin/", "/system/bin/", "/system/sbin/", "/sbin/", "/vendor/bin/", "/su/bin/"};
        for (int i2 = 0; i2 < 6; i2++) {
            try {
                String str = strArr[i2] + "su";
                if (new File(str).exists()) {
                    String strExec = exec(new String[]{"ls", "-l", str});
                    LogUtil.e("isRooted=" + strExec);
                    return !TextUtils.isEmpty(strExec);
                }
            } catch (Exception e2) {
```

## E072 private_key_asset

The asset is a private PEM key. Only type, size and hash are recorded, not its content.

Source: `assets/client.txt; file and sha256sum`

```text
assets/client.txt: PEM RSA private key, 1679 bytes
sha256=c2df4ea5ac3d277b197bf5c2c05c38efb7a4bdf339db4078fb5c2737c1a6bd9a
Evidencia adicional: analysis/apk/evidence/asset-hashes.txt y security-search.txt
```
## E073 shadow_gating

Shadow initialization is gated by a flag that is false by default. No override was found in the own code.

Source: `com/quvii/openapi/QvOpenSDK.java:2160-2163`

```java
        if (SDKConfig.SUPPORT_DEVICE_SHADOW) {
            QvShadowHelper.INSTANCE.init();
        }
        QvCore.getInstance().start();
```
## E074 legacy_http_schemes

The unbind route forces HTTP and the general selector depends on the device and cgiScheme. A successful runtime connection is not claimed.

Source: `com/quvii/qvweb/publico/utils/RetrofitUtil.java:299-309,433-482`

```java
    public static DeviceApi getUnbindDeviceApiUrl(QvDevice qvDevice) {
        String useIp = qvDevice.getUseIp();
        String str = qvDevice.getCgiScheme() + useIp;
        Map<String, DeviceApi> map = deviceApiMap;
        DeviceApi deviceApi = map.get(str);
        if (deviceApi != null) {
            return deviceApi;
        }
        DeviceApi deviceApi2 = (DeviceApi) getRetrofit(QvWebConst.HTTP_PROTOCOL + useIp + Operator.Operation.DIVISION, true).create(DeviceApi.class);
        map.put(str, deviceApi2);
        return deviceApi2;
// ...
    public static DeviceApi getDirectDeviceApi(QvDevice qvDevice, boolean z2) {
        String string;
        Retrofit retrofit;
        if (qvDevice.getCgiPort() <= 0) {
            LogUtil.e("port error : " + qvDevice.getCgiPort());
            return null;
        }
        boolean zIsHsDevice = qvDevice.isHsDevice();
        String str = SDKConst.CGI_SCHEME_HTTP;
        String str2 = zIsHsDevice ? SDKConst.CGI_SCHEME_HTTP : SDKConfig.CGI_PROTOCOL;
        if (qvDevice.isHsDevice() || qvDevice.isVsuDevice()) {
            if (qvDevice.getCgiScheme() != 0) {
                str = SDKConst.CGI_SCHEME_HTTPS;
            }
            str2 = str;
        }
        String useIp = qvDevice.getUseIp();
        if (!SDKConfig.IS_OPEN_AUTH) {
            StringBuilder sb = new StringBuilder();
            sb.append(str2);
            sb.append(useIp);
            sb.append(qvDevice.getCgiPort());
            sb.append(z2 ? "_log-open" : "_log-close");
            string = sb.toString();
        } else if (qvDevice.getQvDeviceAbility().isSupportDeviceHttpAuth() || qvDevice.isIpDeviceSupportAuth) {
            string = str2 + useIp + qvDevice.getCgiPort() + "_auth_" + qvDevice.getUmid();
            DeviceAuthHeaderInterceptor deviceAuthHeaderInterceptor = SDKVariates.authHeaderInterceptorMap.get(qvDevice.getUmid());
            if (deviceAuthHeaderInterceptor == null) {
                deviceAuthHeaderInterceptor = new DeviceAuthHeaderInterceptor();
            }
            deviceAuthHeaderInterceptor.updateDevice(qvDevice);
            SDKVariates.authHeaderInterceptorMap.put(qvDevice.getUmid(), deviceAuthHeaderInterceptor);
        } else {
            StringBuilder sb2 = new StringBuilder();
            sb2.append(str2);
            sb2.append(useIp);
            sb2.append(qvDevice.getCgiPort());
            sb2.append(z2 ? "_log-open" : "_log-close");
            string = sb2.toString();
        }
        Map<String, DeviceApi> map = deviceApiMap;
        DeviceApi deviceApi = map.get(string);
        if (deviceApi != null) {
            return deviceApi;
        }
        String str3 = str2 + "://" + useIp + ":" + qvDevice.getCgiPort() + Operator.Operation.DIVISION;
        if (SDKConfig.IS_OPEN_AUTH) {
            retrofit = (qvDevice.getQvDeviceAbility().isSupportDeviceHttpAuth() || qvDevice.isIpDeviceSupportAuth) ? getXmlDeviceAuthRetrofit(str3, qvDevice) : getRetrofit(str3, z2);
        } else {
            retrofit = getRetrofit(str3, z2);
```
## E075 file_provider_paths

FileProvider is not exported, but it allows URI grants and its configuration includes a broad root and external-path. Exploitation is not claimed.

Source: `res/xml/provider_paths.xml:3-17` and `res/xml/permission_file_paths.xml:3-20`

```xml
<files-path name="hm_private_cache" path="logs_out"/>
<external-path name="external_files" path="."/>
<root-path name="root_path" path=""/>
<!-- permission_file_paths: files-path, cache-path, external-path, external-files-path, external-cache-path and external-media-path, all with path="." -->
```
