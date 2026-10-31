# Evidencia de pantallas y estados

Extractos de la salida JADX 1.5.6. No son capturas ni evidencia de ejecución.

## loading

Entrada, deep link y llamada.

Origen: `com/quvii/ubell/main/view/LoadingActivity.java:167-203`

```java
    @Override // com.qing.mvpart.base.QvActivity
    protected boolean onPreStart() {
        final Intent intent;
        boolean z2;
        this.mPresenter = createPresenter();
        final AlarmMessageInfo alarmMessageInfo = (AlarmMessageInfo) getIntent().getParcelableExtra(AlarmMessageInfo.NAME);
        if (alarmMessageInfo != null) {
            LogUtil.i("start show push");
            PushHelper.stopPushService(this);
            if (alarmMessageInfo.getAlarmEvent() != 19) {
                intent = null;
                z2 = false;
            } else {
                boolean booleanExtra = getIntent().getBooleanExtra(AppConst.INTENT_APP_IN_LOCK, true);
                if (!booleanExtra) {
                    PushHelper.AddPushAcceptRecord(alarmMessageInfo);
                }
                intent = new Intent(this, (Class<?>) (booleanExtra ? PushCallActivity.class : PreviewActivity.class));
                z2 = true;
            }
            if (z2) {
                this.alarmMessageInfo = alarmMessageInfo;
                getWindow().getDecorView().post(new Runnable() { // from class: com.quvii.ubell.main.view.b
                    @Override // java.lang.Runnable
                    public final void run() {
                        this.f9607a.lambda$onPreStart$0(intent, alarmMessageInfo);
                    }
                });
                return true;
            }
        }
        if (isTaskRoot() || !SdkManager.getInstance().isInitState()) {
            return true;
        }
        finish();
        return false;
    }
```

## cache

Relay sin layout.

Origen: `com/quvii/ubell/push/view/CacheActivity.java:11-22`

```java
public class CacheActivity extends AppCompatActivity {
    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        AlarmMessageInfo alarmMessageInfo = (AlarmMessageInfo) getIntent().getParcelableExtra(AlarmMessageInfo.NAME);
        Intent intent = new Intent(this, (Class<?>) LoadingActivity.class);
        intent.putExtra(AppConst.INTENT_APP_IN_LOCK, getIntent().getBooleanExtra(AppConst.INTENT_APP_IN_LOCK, true));
        intent.putExtra(AppConst.INTENT_APP_IN_BACKGROUND, getIntent().getBooleanExtra(AppConst.INTENT_APP_IN_BACKGROUND, false));
        intent.putExtra(AlarmMessageInfo.NAME, alarmMessageInfo);
        startActivity(intent);
        finish();
    }
```

## main_tab

Creación y selección de fragments.

Origen: `com/quvii/ubell/main/view/MainTabActivity.java:214-256`

```java
    public void initView(Bundle bundle) {
        if (bundle != null) {
            FragmentManager supportFragmentManager = getSupportFragmentManager();
            this.deviceFragment = (MainDeviceListFragment) supportFragmentManager.h0(MainDeviceListFragment.class.getName());
            this.messageFragment = (MainMessageListFragment) supportFragmentManager.h0(MainMessageListFragment.class.getName());
            this.fileFragment = (MainFileFragment) supportFragmentManager.h0(MainFileFragment.class.getName());
            this.meFragment = (MainMeFragment) supportFragmentManager.h0(MainMeFragment.class.getName());
            this.currentFragmentNum = bundle.getInt(SAVE_CURRENT_FRAGMENT);
            this.isStarted = bundle.getBoolean(SAVE_STARTED);
        }
        MainDeviceListFragment mainDeviceListFragment = this.deviceFragment;
        if (mainDeviceListFragment == null) {
            mainDeviceListFragment = new MainDeviceListFragment();
        }
        this.deviceFragment = mainDeviceListFragment;
        MainMessageListFragment mainMessageListFragment = this.messageFragment;
        if (mainMessageListFragment == null) {
            mainMessageListFragment = new MainMessageListFragment();
        }
        this.messageFragment = mainMessageListFragment;
        MainFileFragment mainFileFragment = this.fileFragment;
        if (mainFileFragment == null) {
            mainFileFragment = new MainFileFragment();
        }
        this.fileFragment = mainFileFragment;
        MainMeFragment mainMeFragment = this.meFragment;
        if (mainMeFragment == null) {
            mainMeFragment = new MainMeFragment();
        }
        this.meFragment = mainMeFragment;
        this.messageFragment.setMainTabListener(this);
        int i2 = this.currentFragmentNum;
        if (i2 == 1) {
            this.currentFragment = this.deviceFragment;
        } else if (i2 == 2) {
            this.currentFragment = this.messageFragment;
        } else if (i2 == 3) {
            this.currentFragment = this.fileFragment;
        } else if (i2 == 4) {
            this.currentFragment = this.meFragment;
        }
        switchFragment(this.deviceFragment, 1);
    }
```

## add_type

Alta nueva o en red.

Origen: `com/quvii/ubell/device/add/view/DANewTypeChooseActivity.java:11-46`

```java
public class DANewTypeChooseActivity extends TitlebarBaseActivity<ActivityDaTypeChooseBinding, IPresenter> {
    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$0(View view) {
        startActivity(DANewProduceIntroduceActivity.class);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$1(View view) {
        startActivity(DAOnlineTypeChooseActivity.class);
    }

    @Override // com.qing.mvpart.base.IActivity
    public IPresenter createPresenter() {
        return null;
    }

    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
        setTitlebar(getString(R.string.key_device_add));
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void setListener() {
        super.setListener();
        ((ActivityDaTypeChooseBinding) this.binding).btAddNew.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.device.add.view.a0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9385a.lambda$setListener$0(view);
            }
        });
        ((ActivityDaTypeChooseBinding) this.binding).btAddOnline.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.device.add.view.b0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9387a.lambda$setListener$1(view);
            }
        });
```

## preview

Vídeo, foco y surface.

Origen: `com/quvii/ubell/video/view/PreviewActivity.java:721-746`

```java
    private void setDevice(final int i2, final Device device) {
        MyGLSurfaceView myGLSurfaceView = new MyGLSurfaceView(this.mContext);
        myGLSurfaceView.setOnViewSizeChangeListener(new MyGLSurfaceView.OnViewSizeChangeListener() { // from class: com.quvii.ubell.video.view.h0
            @Override // com.quvii.qvplayer.view.MyGLSurfaceView.OnViewSizeChangeListener
            public final void onChange(int i3, int i4) {
                this.f9879a.lambda$setDevice$37(device, i2, i3, i4);
            }
        });
        myGLSurfaceView.setOnSurfaceTouchEvent(new MyGLSurfaceView.OnSurfaceTouchEvent() { // from class: com.quvii.ubell.video.view.PreviewActivity.14
            @Override // com.quvii.qvplayer.view.MyGLSurfaceView.OnSurfaceTouchEvent
            public void onDoubleTap() {
                if (PreviewActivity.this.windowNum == 1) {
                    PreviewActivity.this.windowNum = 4;
                } else {
                    PreviewActivity.this.windowNum = 1;
                }
            }

            @Override // com.quvii.qvplayer.view.MyGLSurfaceView.OnSurfaceTouchEvent
            public void onDown() {
                PreviewActivity.this.orientationEventListener.isPortrait();
            }
        });
        this.surfaceViewList.set(i2, myGLSurfaceView);
        ((PreviewContract.Presenter) getP()).setDevice(i2, device, myGLSurfaceView);
        ((PreviewContract.Presenter) getP()).setCurrentFocus(i2);
```

## online_type

Buscar o alta manual.

Origen: `com/quvii/ubell/device/add/view/DAOnlineTypeChooseActivity.java:13-50`

```java
public class DAOnlineTypeChooseActivity extends TitlebarBaseActivity<ActivityDaOnlineTypeChooseBinding, IPresenter> {
    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$0(View view) {
        startActivity(DAOnlineDeviceListActivity.class);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$1(View view) {
        Intent intent = new Intent(this.mContext, (Class<?>) DADeviceBindActivity.class);
        intent.putExtra(DAConst.INTENT_DEVICE_BIND_TYPE, 1);
        startActivity(intent);
    }

    @Override // com.qing.mvpart.base.IActivity
    public IPresenter createPresenter() {
        return null;
    }

    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
        setTitlebar(getString(R.string.key_device_online));
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void setListener() {
        super.setListener();
        ((ActivityDaOnlineTypeChooseBinding) this.binding).btAddOnline.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.device.add.view.e0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9396a.lambda$setListener$0(view);
            }
        });
        ((ActivityDaOnlineTypeChooseBinding) this.binding).btnAddManual.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.device.add.view.f0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9398a.lambda$setListener$1(view);
            }
        });
```

## product_choose

Tipos de producto.

Origen: `com/quvii/ubell/device/add/view/DANewProduceChooseActivity.java:13-52`

```java
public class DANewProduceChooseActivity extends TitlebarBaseActivity<ActivityDaNewProduceChooseBinding, IPresenter> {
    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$0(Intent intent, View view) {
        intent.putExtra(DAConst.DEVICE_ADD_NEW_PRODUCT_TYPE, 1);
        startActivity(intent);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$1(Intent intent, View view) {
        intent.putExtra(DAConst.DEVICE_ADD_NEW_PRODUCT_TYPE, 0);
        startActivity(intent);
    }

    @Override // com.qing.mvpart.base.IActivity
    public IPresenter createPresenter() {
        return null;
    }

    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
        setTitlebar(getString(R.string.key_setup_wizard), true);
        setRightBackBtn();
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void setListener() {
        super.setListener();
        final Intent intent = new Intent(this.mContext, (Class<?>) DANewProduceIntroduceActivity.class);
        ((ActivityDaNewProduceChooseBinding) this.binding).llType7410.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.device.add.view.v
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9428a.lambda$setListener$0(intent, view);
            }
        });
        ((ActivityDaNewProduceChooseBinding) this.binding).llType9440.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.device.add.view.w
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9430a.lambda$setListener$1(intent, view);
            }
        });
```

## product_intro

Permisos y escaneo.

Origen: `com/quvii/ubell/device/add/view/DANewProduceIntroduceActivity.java:59-75`

```java
    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$0(View view) {
        if (checkLocation()) {
            Activity activity = this.mContext;
            QvPermissionUtils.location(activity, new QvPermissionUtils.SimpleRequestPermission(activity) { // from class: com.quvii.ubell.device.add.view.DANewProduceIntroduceActivity.2
                @Override // com.quvii.qvlib.util.QvPermissionUtils.RequestPermission
                public void onRequestPermissionSuccess() {
                    QvPermissionUtils.launchCamera(((QvActivity) DANewProduceIntroduceActivity.this).mContext, new QvPermissionUtils.SimpleRequestPermission(((QvActivity) DANewProduceIntroduceActivity.this).mContext) { // from class: com.quvii.ubell.device.add.view.DANewProduceIntroduceActivity.2.1
                        @Override // com.quvii.qvlib.util.QvPermissionUtils.RequestPermission
                        public void onRequestPermissionSuccess() {
                            DANewProduceIntroduceActivity.this.startActivityForResult(new Intent(((QvActivity) DANewProduceIntroduceActivity.this).mContext, (Class<?>) DACaptureActivity.class), 100);
                        }
                    });
                }
            });
        }
    }
```

## handle_wifi

Comprobación del AP.

Origen: `com/quvii/ubell/device/add/view/DANewHandleWifiActivity.java:26-67`

```java
    private void checkWifi() {
        LogUtil.i("WifiAvailable: " + Utils.isWifiAvailable(this));
        String currentWifiName = QvNetUtil.getCurrentWifiName(this);
        LogUtil.i("Wifi Name: " + currentWifiName + "device Ap Name: " + this.deviceAddInfo.getApName());
        if (DeviceHelper.getInstance().isMyDevice(this.deviceAddInfo.getUid(), currentWifiName)) {
            this.deviceAddInfo.setApName(currentWifiName);
            startActivity(getIntent().getBooleanExtra(AppConst.INTENT_HANDLE_WIFI_AND_BACK, false) ? DAWifiSetActivity.class : DARouterInfoActivity.class);
            return;
        }
        Snackbar newInstance = SnackBarUtil.getNewInstance(this.mTitlebar, getString(R.string.key_handle_wifi_hint2) + " " + this.deviceAddInfo.getUid().substring(this.deviceAddInfo.getUid().length() - 4, this.deviceAddInfo.getUid().length()), 0);
        newInstance.setAction(R.string.key_open, new View.OnClickListener() { // from class: com.quvii.ubell.device.add.view.s
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9423a.lambda$checkWifi$2(view);
            }
        });
        newInstance.setActionTextColor(getResources().getColor(R.color.titleText));
        newInstance.show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$checkWifi$2(View view) {
        try {
            startActivity(new Intent("android.settings.WIFI_SETTINGS"));
        } catch (Exception e2) {
            LogUtil.printStackTrace(e2);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$0(View view) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService("clipboard");
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(Constants.ScionAnalytics.PARAM_LABEL, this.deviceAddInfo.getUid()));
            showSnackBar(R.string.key_copied);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$1(View view) {
        checkWifi();
    }
```

## wifi_list_legacy

Modos de lista WiFi.

Origen: `com/quvii/ubell/device/manage/view/DMWifiListActivity.java:51-71`

```java
    public /* synthetic */ void lambda$setListener$2(AdapterView adapterView, View view, final int i2, long j2) {
        int i3 = this.mode;
        if (i3 == 1) {
            ((DMWifiListContract.Presenter) getP()).connectTargetWifi(this.wifiList.get(i2));
            return;
        }
        if (i3 != 2) {
            startActivity(DMWifiSetActivity.class, new TitlebarBaseDeviceAddActivity.Interceptor() { // from class: com.quvii.ubell.device.manage.view.h0
                @Override // com.quvii.ubell.publico.base.TitlebarBaseDeviceAddActivity.Interceptor
                public final Intent onInterceptor(Intent intent) {
                    return this.f9506a.lambda$setListener$1(i2, intent);
                }
            });
            return;
        }
        this.deviceAddInfo.setTargetName(QvWifiUtil.GetRealSsid(this.wifiList.get(i2).SSID));
        Intent intent = new Intent();
        intent.putExtra(DeviceAddInfo.NAME, this.deviceAddInfo);
        setResult(0, intent);
        finish();
    }
```

## wifi_set_legacy

Estados de configuración WiFi.

Origen: `com/quvii/ubell/device/manage/view/DMWifiSetActivity.java:126-211`

```java
    @Override // com.quvii.ubell.device.manage.contract.DMWifiSetContract.View
    public void showConnectError() {
        DeviceAddInfo deviceAddInfo = this.deviceAddInfo;
        new androidx.appcompat.app.c.a(this).setMessage(getString(R.string.key_device_ap_connect_hint) + (deviceAddInfo != null ? deviceAddInfo.getApName() : "")).setPositiveButton(R.string.key_confirm, new DialogInterface.OnClickListener() { // from class: com.quvii.ubell.device.manage.view.m0
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i2) {
                this.f9519a.lambda$showConnectError$2(dialogInterface, i2);
            }
        }).setNegativeButton(R.string.key_cancel, new DialogInterface.OnClickListener() { // from class: com.quvii.ubell.device.manage.view.n0
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i2) {
                DMWifiSetActivity.lambda$showConnectError$3(dialogInterface, i2);
            }
        }).show();
    }

    @Override // com.quvii.ubell.device.manage.contract.DMWifiSetContract.View
    public void showCountDown(int i2) {
        showMyLoading();
        QvCountDownUtil qvCountDownUtil = this.countDownUtil;
        if (qvCountDownUtil != null) {
            qvCountDownUtil.release();
        }
        QvCountDownUtil qvCountDownUtil2 = new QvCountDownUtil();
        this.countDownUtil = qvCountDownUtil2;
        qvCountDownUtil2.setTimeOut(i2);
        this.countDownUtil.setListener(new QvCountDownUtil.Listener() { // from class: com.quvii.ubell.device.manage.view.DMWifiSetActivity.1
            @Override // com.quvii.qvlib.util.QvCountDownUtil.Listener
            public void onComplete() {
                DMWifiSetActivity.this.hideMyLoading();
                ((DMWifiSetContract.Presenter) DMWifiSetActivity.this.getP()).timeout();
            }

            @Override // com.quvii.qvlib.util.QvCountDownUtil.Listener
            public void onCount(int i3) {
                if (DMWifiSetActivity.this.loadingDialog == null || !DMWifiSetActivity.this.loadingDialog.isShowing()) {
                    return;
                }
                DMWifiSetActivity.this.loadingDialog.updateInfo(DMWifiSetActivity.this.getString(R.string.key_device_add_wait_device_online) + i3);
            }
        });
        this.countDownUtil.start();
    }

    @Override // com.quvii.ubell.device.manage.contract.DMWifiSetContract.View
    public void showModifyWifiSuccess() {
        startActivity(DMConfigActivity.class);
        finish();
    }

    public void showMyLoading() {
        if (this.loadingDialog == null) {
            MyLoadingDialog myLoadingDialog = new MyLoadingDialog(this);
            this.loadingDialog = myLoadingDialog;
            myLoadingDialog.setInfoVisible(true);
            this.loadingDialog.setCancelable(false);
        }
        this.loadingDialog.show();
    }

    @Override // com.quvii.ubell.device.manage.contract.DMWifiSetContract.View
    public void showSetWifiFail(boolean z2, String str) {
        hideMyLoading();
        QvCountDownUtil qvCountDownUtil = this.countDownUtil;
        if (qvCountDownUtil != null) {
            qvCountDownUtil.release();
        }
        showMessage(str);
        if (z2) {
            startActivity(DANewProduceIntroduceActivity.class);
            finish();
        }
    }

    @Override // com.quvii.ubell.device.manage.contract.DMWifiSetContract.View
    public void showSetWifiSuccess(Class<?> cls) {
        hideMyLoading();
        QvCountDownUtil qvCountDownUtil = this.countDownUtil;
        if (qvCountDownUtil != null) {
            qvCountDownUtil.release();
        }
        Intent intent = new Intent(this.mContext, cls);
        intent.putExtra(DeviceAddInfo.NAME, this.deviceAddInfo);
        startActivity(intent);
        finish();
    }
```

## device_bind

Formulario y enlace compartido.

Origen: `com/quvii/ubell/device/add/view/DADeviceBindActivity.java:82-145`

```java
    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$1(View view) {
        Editable text = ((ActivityDaDeviceBindBinding) this.binding).etInputShareLink.getText();
        if (TextUtils.isEmpty(text)) {
            this.deviceAddInfo.setAuthCode(((ActivityDaDeviceBindBinding) this.binding).etDevicePassword.getText().toString());
            this.deviceAddInfo.setUid(((ActivityDaDeviceBindBinding) this.binding).etUid.getText().toString());
            this.deviceAddInfo.setName(((ActivityDaDeviceBindBinding) this.binding).etDeviceName.getText().toString());
            this.deviceAddInfo.setUsername("adminapp");
            this.deviceAddInfo.setUserCameraNum(1);
            this.deviceAddInfo.setUserCctvNum(0);
            ((DADeviceBindContract.Presenter) getP()).bindDevice(this.deviceAddInfo);
            return;
        }
        DeviceShareInfo deviceShareInfoCheckClipboardInfo = DeviceShareManager.getInstance().checkClipboardInfo(text.toString());
        if (deviceShareInfoCheckClipboardInfo == null || TextUtils.isEmpty(deviceShareInfoCheckClipboardInfo.getUid())) {
            showMessage(R.string.key_uid_empty_hint);
        } else if (AppDatabase.getDevice(deviceShareInfoCheckClipboardInfo.getUid()) != null) {
            QvToastUtil.showL(R.string.key_device_exist);
        } else {
            LogUtil.i("find share device");
            ((DADeviceBindContract.Presenter) getP()).bindDevice(((ActivityDaDeviceBindBinding) this.binding).etDeviceName.getText().toString(), deviceShareInfoCheckClipboardInfo);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$2(View view) {
        Activity activity = this.mContext;
        QvPermissionUtils.launchCamera(activity, new QvPermissionUtils.SimpleRequestPermission(activity) { // from class: com.quvii.ubell.device.add.view.DADeviceBindActivity.3
            @Override // com.quvii.qvlib.util.QvPermissionUtils.RequestPermission
            public void onRequestPermissionSuccess() {
                DADeviceBindActivity.this.startActivityForResult(new Intent(((QvActivity) DADeviceBindActivity.this).mContext, (Class<?>) DACaptureActivity.class), 0);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$3(View view) {
        hideSoftInput();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void switchMode(boolean z2) {
        ((ActivityDaDeviceBindBinding) this.binding).etUid.setEnabled(z2);
        ((ActivityDaDeviceBindBinding) this.binding).ivQr.setEnabled(z2);
        ((ActivityDaDeviceBindBinding) this.binding).etDevicePassword.setEnabled(z2);
        ((ActivityDaDeviceBindBinding) this.binding).ivShowPwd.setEnabled(z2);
    }

    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
        SoftHideKeyBoardUtil.assistActivity(this);
        this.bindMode = getIntent().getIntExtra(DAConst.INTENT_DEVICE_BIND_TYPE, -1) == 1;
        setTitlebar(getString(R.string.key_device_add), this.bindMode);
        if (!this.bindMode) {
            setRightBackBtn();
        }
        T t2 = this.binding;
        Utils.ViewAddAColon(((ActivityDaDeviceBindBinding) t2).tvName, ((ActivityDaDeviceBindBinding) t2).tvUid, ((ActivityDaDeviceBindBinding) t2).tvPassword);
        ((ActivityDaDeviceBindBinding) this.binding).tvPassword.setVisibility(this.bindMode ? 0 : 8);
        ((ActivityDaDeviceBindBinding) this.binding).etDevicePassword.setVisibility(this.bindMode ? 0 : 8);
        ((ActivityDaDeviceBindBinding) this.binding).ivShowPwd.setVisibility(this.bindMode ? 0 : 8);
        ((ActivityDaDeviceBindBinding) this.binding).tvHint.setText(this.bindMode ? R.string.key_device_online_add_hint : R.string.key_device_new_add_hint);
        ((DADeviceBindContract.Presenter) getP()).setBindMode(this.bindMode);
    }
```

## online_devices

Escaneo y lista online.

Origen: `com/quvii/ubell/device/add/view/DAOnlineDeviceListActivity.java:58-100`

```java
    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void processLogic() {
        super.processLogic();
        OnlineDeviceListAdapter onlineDeviceListAdapter = new OnlineDeviceListAdapter(this, this.onlineDevices);
        this.listAdapter = onlineDeviceListAdapter;
        ((ActivityDaOnlineDeviceListBinding) this.binding).lvDevices.setAdapter((ListAdapter) onlineDeviceListAdapter);
        ((DAOnlineDeviceListContract.Presenter) getP()).getOnlineDevices();
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void setListener() {
        super.setListener();
        ((ActivityDaOnlineDeviceListBinding) this.binding).srlMain.setColorSchemeResources(android.R.color.holo_blue_bright, android.R.color.holo_green_light, android.R.color.holo_orange_light, android.R.color.holo_red_light);
        ((ActivityDaOnlineDeviceListBinding) this.binding).srlMain.setOnRefreshListener(new SwipeRefreshLayout.j() { // from class: com.quvii.ubell.device.add.view.c0
            @Override // androidx.swiperefreshlayout.widget.SwipeRefreshLayout.j
            public final void onRefresh() {
                this.f9391a.lambda$setListener$0();
            }
        });
        ((ActivityDaOnlineDeviceListBinding) this.binding).lvDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() { // from class: com.quvii.ubell.device.add.view.d0
            @Override // android.widget.AdapterView.OnItemClickListener
            public final void onItemClick(AdapterView adapterView, View view, int i2, long j2) {
                this.f9394a.lambda$setListener$1(adapterView, view, i2, j2);
            }
        });
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.QvActivity, com.qing.mvpart.mvp.IView
    public void showLoading() {
        showRefresh();
    }

    @Override // com.quvii.ubell.device.add.contract.DAOnlineDeviceListContract.View
    public void showOnlineDevices(List<OnlineDevice> list) {
        this.onlineDevices.clear();
        this.onlineDevices.addAll(list);
        this.listAdapter.notifyDataSetChanged();
    }

    @Override // com.quvii.ubell.device.add.contract.DAOnlineDeviceListContract.View
    public void showRefresh() {
        ((ActivityDaOnlineDeviceListBinding) this.binding).srlMain.setRefreshing(true);
    }
```

## scan

Resultados QR.

Origen: `com/quvii/ubell/device/add/view/DACaptureActivity.java:270-335`

```java
    @Override // com.quvii.ubell.publico.activity.BaseScanQrActivity
    public void onDecode(String info) {
        kotlin.jvm.internal.j.e(info, "info");
        LogUtil.i("onDecode: " + info);
        if (info.equals("")) {
            QvToastUtil.showS(getString(R.string.key_scan_fail));
            restartPreviewAfterDelay(1000L);
            return;
        }
        int i2 = this.mode;
        if (i2 != 0) {
            if (i2 == 1) {
                DeviceHelper.dealWithQrInfo(info, new DeviceHelper.QrInfoCallBack() { // from class: com.quvii.ubell.device.add.view.DACaptureActivity.onDecode.1
                    @Override // com.quvii.ubell.publico.sdk.DeviceHelper.QrInfoCallBack
                    public void findDeviceAddInfo(DeviceAddInfo deviceAddInfo) {
                        kotlin.jvm.internal.j.e(deviceAddInfo, "deviceAddInfo");
                        DACaptureActivity.this.startTargetActivity(DANewProduceIntroduceActivity.class, deviceAddInfo);
                    }

                    @Override // com.quvii.ubell.publico.sdk.DeviceHelper.QrInfoCallBack
                    public void findDeviceShareInfo(DeviceShareInfo deviceShareInfo) {
                        kotlin.jvm.internal.j.e(deviceShareInfo, "deviceShareInfo");
                        DeviceShareManager.getInstance().dealWithDeviceShareInfo(DACaptureActivity.this, deviceShareInfo);
                        DACaptureActivity.this.finish();
                    }

                    @Override // com.quvii.ubell.publico.sdk.DeviceHelper.QrInfoCallBack
                    public void noFind() {
                        DACaptureActivity.this.showNotDeviceDialog();
                    }
                });
                return;
            } else {
                if (i2 != 2) {
                    return;
                }
                DeviceHelper.dealWithQrInfo(info, new DeviceHelper.QrInfoCallBack() { // from class: com.quvii.ubell.device.add.view.DACaptureActivity.onDecode.2
                    @Override // com.quvii.ubell.publico.sdk.DeviceHelper.QrInfoCallBack
                    public void findDeviceAddInfo(DeviceAddInfo deviceAddInfo) {
                        kotlin.jvm.internal.j.e(deviceAddInfo, "deviceAddInfo");
                        DACaptureActivity.this.startTargetActivity(DADeviceBindActivity.class, deviceAddInfo);
                    }

                    @Override // com.quvii.ubell.publico.sdk.DeviceHelper.QrInfoCallBack
                    public void findDeviceShareInfo(DeviceShareInfo deviceShareInfo) {
                        kotlin.jvm.internal.j.e(deviceShareInfo, "deviceShareInfo");
                        DeviceShareManager.getInstance().dealWithDeviceShareInfo(DACaptureActivity.this, deviceShareInfo);
                        DACaptureActivity.this.finish();
                    }

                    @Override // com.quvii.ubell.publico.sdk.DeviceHelper.QrInfoCallBack
                    public void noFind() {
                        QvToastUtil.showS(R.string.key_scan_fail);
                        DACaptureActivity.this.restartPreviewAfterDelay(1000L);
                    }
                });
                return;
            }
        }
        Intent intent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putString(SCAN_RESULT, info);
        intent.putExtras(bundle);
        setResult(-1, intent);
        finish();
    }
```

## config

Visibilidad según compartición.

Origen: `com/quvii/ubell/device/manage/view/DMConfigActivity.java:207-235`

```java
    @Override // com.quvii.ubell.publico.base.TitlebarBaseDeviceActivity, com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void processLogic() {
        super.processLogic();
        Device device = this.device;
        if (device == null) {
            return;
        }
        int talkMode = device.getTalkMode();
        if (talkMode < 0) {
            talkMode = 0;
        }
        this.talkModeRes = new String[]{getString(R.string.key_hold_talk), getString(R.string.key_full_duplex_speech)};
        int fps = this.device.getFps();
        this.previewModeRes = new String[]{getString(R.string.key_video_fps_high), getString(R.string.key_video_fps_low), getString(R.string.key_video_fps_picture)};
        this.bottomDialogTalkMode.setData(this.talkModeRes, talkMode);
        this.bottomDialogPreviewMode.setData(this.previewModeRes, fps);
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

## password_modify

Modos de cambio de contraseña.

Origen: `com/quvii/ubell/device/manage/view/DMPasswordModifyActivity.java:60-103`

```java
    @Override // com.quvii.ubell.publico.base.TitlebarBaseDeviceActivity, com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void processLogic() {
        super.processLogic();
        final int intExtra = getIntent().getIntExtra(AppConst.INTENT_MODIFY_TYPE, 0);
        ((DMPasswordModifyContract.Presenter) getP()).setDevice(this.device, intExtra);
        T t2 = this.binding;
        RtlUtils.dealWithEdit(((ActivityDmPasswordModifyBinding) t2).etOldPass, ((ActivityDmPasswordModifyBinding) t2).etNewPass, ((ActivityDmPasswordModifyBinding) t2).etConfirmPass, ((ActivityDmPasswordModifyBinding) t2).etNew2, ((ActivityDmPasswordModifyBinding) t2).etConfirm2);
        if (intExtra != 1) {
            int i2 = R.string.key_modify_device_password;
            if (intExtra != 3) {
                if (this.device.getDeviceAbility().isSupportNoLoginShare()) {
                    i2 = R.string.key_modify_admin_password;
                }
                setTitlebar(getString(i2));
                ((ActivityDmPasswordModifyBinding) this.binding).tvAdmin.setVisibility(8);
                ((ActivityDmPasswordModifyBinding) this.binding).tvUnlock.setVisibility(8);
                ((ActivityDmPasswordModifyBinding) this.binding).llModifyNew2.setVisibility(8);
                ((ActivityDmPasswordModifyBinding) this.binding).llModifyConfirm2.setVisibility(8);
            } else {
                setTitlebar(getString(R.string.key_modify_device_password));
                ((ActivityDmPasswordModifyBinding) this.binding).llOld.setVisibility(8);
                if (!this.device.getDeviceAbility().isSupportNoLoginShare()) {
                    ((ActivityDmPasswordModifyBinding) this.binding).tvAdmin.setVisibility(8);
                    ((ActivityDmPasswordModifyBinding) this.binding).tvUnlock.setVisibility(8);
                    ((ActivityDmPasswordModifyBinding) this.binding).llModifyNew2.setVisibility(8);
                    ((ActivityDmPasswordModifyBinding) this.binding).llModifyConfirm2.setVisibility(8);
                }
            }
        } else {
            setTitlebar(getString(R.string.key_modify_unlock_password));
            ((ActivityDmPasswordModifyBinding) this.binding).etOldPass.setInputType(2);
            ((ActivityDmPasswordModifyBinding) this.binding).etOldPass.setTransformationMethod(PasswordTransformationMethod.getInstance());
            ((ActivityDmPasswordModifyBinding) this.binding).tvAdmin.setVisibility(8);
            ((ActivityDmPasswordModifyBinding) this.binding).tvUnlock.setVisibility(8);
            ((ActivityDmPasswordModifyBinding) this.binding).llModifyNew2.setVisibility(8);
            ((ActivityDmPasswordModifyBinding) this.binding).llModifyConfirm2.setVisibility(8);
        }
        setRightBtn(R.drawable.selector_btn_save, new View.OnClickListener() { // from class: com.quvii.ubell.device.manage.view.w
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9530a.lambda$processLogic$0(intExtra, view);
            }
        });
    }
```

## unlock_setting

Quick unlock y biometría.

Origen: `com/quvii/ubell/device/manage/view/DMUnlockSettingActivity.java:44-95`

```java
    private void setSwitchStatus() {
        ((ActivityDmUnlockSettingBinding) this.binding).rlBiologicalUnlock.setVisibility(QvBiometricUtil.IsCanAuthenticate() ? 0 : 8);
        ImageView imageView = ((ActivityDmUnlockSettingBinding) this.binding).ivBiologicalUnlock;
        boolean zIsDeviceSwitchStateBiologicalUnlock = this.device.isDeviceSwitchStateBiologicalUnlock();
        int i2 = R.drawable.btn_switch_off;
        imageView.setImageResource(zIsDeviceSwitchStateBiologicalUnlock ? R.drawable.btn_switch_on : R.drawable.btn_switch_off);
        ImageView imageView2 = ((ActivityDmUnlockSettingBinding) this.binding).ivQuickUnlock;
        if (this.device.isDeviceSwitchStateNoPasswordUnlock()) {
            i2 = R.drawable.btn_switch_on;
        }
        imageView2.setImageResource(i2);
    }

    private void showBiologicalUnlockHintDialog() {
        final MyDialogWithNeverShow myDialogWithNeverShow = new MyDialogWithNeverShow(this);
        myDialogWithNeverShow.setMessage(R.string.key_biological_unlock_tip);
        myDialogWithNeverShow.setTitle(R.string.key_warning);
        myDialogWithNeverShow.setCheckStr(R.string.key_alarm_permission_show);
        myDialogWithNeverShow.setCanceledOnTouchOutside(false);
        myDialogWithNeverShow.setNegativeClickListener(R.string.no, new MyDialogWithNeverShow.onNegativeClickListener() { // from class: com.quvii.ubell.device.manage.view.x
            @Override // com.quvii.ubell.publico.widget.MyDialogWithNeverShow.onNegativeClickListener
            public final void onClick() {
                myDialogWithNeverShow.dismiss();
            }
        });
        myDialogWithNeverShow.setNeverShow(SpUtil.getInstance().isNeverShowBiologicalUnlockHint());
        myDialogWithNeverShow.setPositiveClickListener(R.string.key_ok, new MyDialogWithNeverShow.onPositiveClickListener() { // from class: com.quvii.ubell.device.manage.view.y
            @Override // com.quvii.ubell.publico.widget.MyDialogWithNeverShow.onPositiveClickListener
            public final void onClick() {
                this.f9533a.lambda$showBiologicalUnlockHintDialog$1(myDialogWithNeverShow);
            }
        });
        myDialogWithNeverShow.show();
    }

    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
    }

    @Override // com.quvii.ubell.publico.base.TitlebarBaseDeviceActivity, com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void processLogic() {
        super.processLogic();
        setTitlebar(getString(R.string.key_unlock_setting));
        setSwitchStatus();
        ((DMUnlockSettingContract.Presenter) getP()).setDevice(this.device);
        setRightBtn(R.drawable.selector_btn_save, new View.OnClickListener() { // from class: com.quvii.ubell.device.manage.view.z
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9535a.lambda$processLogic$0(view);
            }
        });
    }
```

## device_info

Lectura, edición y guardado.

Origen: `com/quvii/ubell/device/manage/view/DMDeviceInfoEditActivity.java:34-85`

```java
    private void save() {
        ((DMDeviceInfoEditContract.Presenter) getP()).modify(this.device, new DeviceModifyInfo(this.device.getUsername(), ((ActivityDmDeviceInfoEditBinding) this.binding).etDeviceName.getText().toString(), this.device.getAuthCode(), ((ActivityDmDeviceInfoEditBinding) this.binding).etUid.getText().toString(), 1, 0));
    }

    private void switchMode(Mode mode) {
        boolean z2 = mode == Mode.Edit;
        this.mode = mode;
        ((ActivityDmDeviceInfoEditBinding) this.binding).etDeviceName.setEnabled(z2);
        setRightBtnDrawable(z2 ? R.drawable.selector_btn_save : R.drawable.main_selector_btn_file_edit);
    }

    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
        setTitlebar(getString(R.string.key_device_detail));
        T t2 = this.binding;
        RtlUtils.dealWithEdit(((ActivityDmDeviceInfoEditBinding) t2).etDeviceName, ((ActivityDmDeviceInfoEditBinding) t2).etUid);
        T t3 = this.binding;
        Utils.ViewAddAColon(((ActivityDmDeviceInfoEditBinding) t3).tvName, ((ActivityDmDeviceInfoEditBinding) t3).tvUid);
        switchMode(Mode.Normal);
    }

    @Override // com.quvii.ubell.publico.base.TitlebarBaseDeviceActivity, com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void processLogic() {
        super.processLogic();
        Device device = this.device;
        if (device == null) {
            return;
        }
        ((ActivityDmDeviceInfoEditBinding) this.binding).etDeviceName.setText(device.getDeviceName());
        ((ActivityDmDeviceInfoEditBinding) this.binding).etUid.setText(this.device.getCid());
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void setListener() {
        super.setListener();
        setRightBtn(R.drawable.main_selector_btn_file_edit, new View.OnClickListener() { // from class: com.quvii.ubell.device.manage.view.v
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9529a.lambda$setListener$0(view);
            }
        });
    }

    @Override // com.quvii.ubell.device.manage.contract.DMDeviceInfoEditContract.View
    public void showModifyFail(String str) {
        showMessage(R.string.key_config_fail);
    }

    @Override // com.quvii.ubell.device.manage.contract.DMDeviceInfoEditContract.View
    public void showModifySuccess() {
        showMessage(R.string.key_config_success);
        finish();
```

## about

Política local.

Origen: `com/quvii/ubell/me/view/MeAboutActivity.java:68-86`

```java
    private void showConditionsDialog() {
        String conditionString = getConditionString();
        if (conditionString == null) {
            return;
        }
        MyConditionsDialog myConditionsDialog = new MyConditionsDialog(this);
        this.conditionDialog = myConditionsDialog;
        myConditionsDialog.setMessage(conditionString);
        this.conditionDialog.setPositiveClickListener(new MyConditionsDialog.onPositiveClickListener() { // from class: com.quvii.ubell.me.view.e
            @Override // com.quvii.ubell.publico.widget.MyConditionsDialog.onPositiveClickListener
            public final void onClick() {
                this.f9670a.lambda$showConditionsDialog$4();
            }
        });
        this.conditionDialog.setType(1);
        this.conditionDialog.setCanceledOnTouchOutside(false);
        this.conditionDialog.setCancelable(false);
        this.conditionDialog.show();
    }
```

## image_pager

Modo claro y visor negro.

Origen: `com/quvii/ubell/fileManage/view/FMImagePagerActivity.java:88-135`

```java
    public void changeMode() {
        Mode mode = this.mode;
        Mode mode2 = Mode.white;
        if (mode == mode2) {
            this.mode = Mode.black;
            ((ActivityFmImagePagerBinding) this.binding).rlBackground.setBackgroundColor(-16777216);
            ((ActivityFmImagePagerBinding) this.binding).llEditInfo.setVisibility(8);
            this.mTitlebar.setVisibility(8);
            ImmersionBar immersionBar = ((BaseActivity) this).immersionBar;
            if (immersionBar != null) {
                immersionBar.statusBarColor(R.color.icon_black).init();
                return;
            }
            return;
        }
        if (mode == Mode.black) {
            this.mode = mode2;
            ((ActivityFmImagePagerBinding) this.binding).rlBackground.setBackgroundColor(-1);
            ((ActivityFmImagePagerBinding) this.binding).llEditInfo.setVisibility(0);
            this.mTitlebar.setVisibility(0);
            ImmersionBar immersionBar2 = ((BaseActivity) this).immersionBar;
            if (immersionBar2 != null) {
                immersionBar2.statusBarColor(R.color.colorPrimary).init();
            }
        }
    }

    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
        Intent intent = getIntent();
        if (AppVariates.girdFileList == null) {
            finish();
            return;
        }
        this.gridItem.clear();
        this.gridItem.addAll(AppVariates.girdFileList);
        AppVariates.girdFileList = null;
        this.pagerPosition = intent.getIntExtra("position", 0);
        ((FMImagePagerContract.Presenter) getP()).showView(this.gridItem);
        if (bundle != null) {
            this.pagerPosition = bundle.getInt(STATE_POSITION);
        }
        ((ActivityFmImagePagerBinding) this.binding).vpMain.setCurrentItem(this.pagerPosition);
        setTitlebar(String.valueOf(getString(R.string.viewpager_indicator, Integer.valueOf(this.pagerPosition + 1), Integer.valueOf(((ActivityFmImagePagerBinding) this.binding).vpMain.getAdapter().getCount()))));
        this.mode = Mode.white;
        this.mTitlebar.bringToFront();
        ((ActivityFmImagePagerBinding) this.binding).llEditInfo.bringToFront();
    }
```

## video_player

Controles y seek.

Origen: `com/quvii/ubell/fileManage/view/FMLocalVideoPlayActivity.java:155-208`

```java
    @Override // com.quvii.ubell.fileManage.contract.FMLocalVideoPlayContract.View
    public void setCurrentTime(String str) {
        ((ActivityFmLocalVideoPlayBinding) this.binding).tvCurrent.setText(str);
    }

    @Override // com.quvii.ubell.fileManage.contract.FMLocalVideoPlayContract.View
    public void setEndTime(String str) {
        ((ActivityFmLocalVideoPlayBinding) this.binding).tvTotal.setText(str);
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    @SuppressLint({"ClickableViewAccessibility"})
    public void setListener() {
        super.setListener();
        ((ActivityFmLocalVideoPlayBinding) this.binding).ivVideoBg.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.fileManage.view.g
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9543a.lambda$setListener$0(view);
            }
        });
        ((ActivityFmLocalVideoPlayBinding) this.binding).ivMenuPause.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.fileManage.view.h
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9544a.lambda$setListener$1(view);
            }
        });
        ((ActivityFmLocalVideoPlayBinding) this.binding).ivMenuVoice.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.fileManage.view.i
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9545a.lambda$setListener$2(view);
            }
        });
        ((ActivityFmLocalVideoPlayBinding) this.binding).sbPlayer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.quvii.ubell.fileManage.view.FMLocalVideoPlayActivity.1
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar, int i2, boolean z2) {
                FMLocalVideoPlayActivity.this.seekProgress = i2;
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar seekBar) {
                ((FMLocalVideoPlayContract.Presenter) FMLocalVideoPlayActivity.this.getP()).pausePlay();
                FMLocalVideoPlayActivity.this.showOrHideTools(true, false);
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (seekBar == null || FMLocalVideoPlayActivity.this.getP() == 0 || ((ActivityFmLocalVideoPlayBinding) ((BaseActivity) FMLocalVideoPlayActivity.this).binding).llBottomMenu == null) {
                    return;
                }
                FMLocalVideoPlayActivity.this.showOrHideTools(true, true);
                ((FMLocalVideoPlayContract.Presenter) FMLocalVideoPlayActivity.this.getP()).resumePlay();
                ((FMLocalVideoPlayContract.Presenter) FMLocalVideoPlayActivity.this.getP()).setSeekBarProgress(FMLocalVideoPlayActivity.this.seekProgress);
            }
        });
```

## push_call

Llamada entrante.

Origen: `com/quvii/ubell/push/view/PushCallActivity.java:59-120`

```java
    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void processLogic() {
        super.processLogic();
        if (!AppVariates.curIsCalling) {
            finish();
            startActivity(new Intent(this, (Class<?>) MainTabActivity.class));
            return;
        }
        AppVariates.curIsCalling = false;
        this.alarmMessageInfo = (AlarmMessageInfo) getIntent().getParcelableExtra(AlarmMessageInfo.NAME);
        this.isAppInBackground = getIntent().getBooleanExtra(AppConst.INTENT_APP_IN_BACKGROUND, false);
        if (this.alarmMessageInfo == null) {
            ((PushCallContract.Presenter) getP()).refuse();
        } else {
            ((PushCallContract.Presenter) getP()).receiveMessage(this.alarmMessageInfo);
        }
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void setListener() {
        super.setListener();
        ((ActivityPushCallBinding) this.binding).ivRefuse.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.push.view.a
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9779a.lambda$setListener$0(view);
            }
        });
        ((ActivityPushCallBinding) this.binding).ivAccept.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.push.view.b
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9780a.lambda$setListener$1(view);
            }
        });
    }

    @Override // com.quvii.ubell.push.contract.PushCallContract.View
    public void showAccept() {
        Intent intent = new Intent(this, (Class<?>) PreviewActivity.class);
        intent.putExtra(AlarmMessageInfo.NAME, this.alarmMessageInfo);
        startActivity(intent);
        finish();
    }

    @Override // com.quvii.ubell.push.contract.PushCallContract.View
    public void showDeviceName(String str, int i2) {
        ((ActivityPushCallBinding) this.binding).tvDeviceName.setText(str);
    }

    @Override // com.quvii.ubell.push.contract.PushCallContract.View
    public void showDeviceReceived() {
        showMessage(R.string.key_call_accept);
    }

    @Override // com.quvii.ubell.push.contract.PushCallContract.View
    public void showRefuse() {
        if (!this.isAppInBackground) {
            finish();
        } else {
            moveTaskToBack(true);
            finish();
        }
    }
```

## debug

Estado de debug.

Origen: `com/quvii/ubell/test/view/TestInfoActivity.java:200-220`

```java
    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
        setTitlebar("Debug");
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.QvActivity, androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        ((TestInfoContract.Presenter) getP()).serviceTest(false);
        super.onDestroy();
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void processLogic() {
        super.processLogic();
        Iterator<Device> it = DeviceList.mList.iterator();
        while (it.hasNext()) {
            LogUtil.i(it.next().toString());
        }
        ((TestInfoContract.Presenter) getP()).queryInfo();
        ((ActivityTestInfoBinding) this.binding).tvAdvanced.setVisibility(AppConfig.debug ? 0 : 8);
    }
```

## upgrade

Firmware y progreso.

Origen: `com/quvii/ubell/device/manage/view/DMUpgradeActivity.java:65-138`

```java
    @Override // com.quvii.ubell.device.manage.contract.DMUpgradeContract.View
    public void showDeviceVersion() {
        if (this.device.getCurrentVersion() != null) {
            ((ActivityDmUpgradeBinding) this.binding).tvCurrentVersion.setText(this.device.getCurrentVersion());
        }
        if (this.device.getLatestVersion() != null) {
            ((ActivityDmUpgradeBinding) this.binding).tvLatestVersion.setText(this.device.getLatestVersion());
        } else {
            ((ActivityDmUpgradeBinding) this.binding).tvLatestVersion.setText(this.device.getCurrentVersion());
        }
    }

    @Override // com.quvii.ubell.device.manage.contract.DMUpgradeContract.View
    public void showUpgradeFail() {
        if (this.myHorizontalProgressBar.isShowing()) {
            this.myHorizontalProgressBar.dismiss();
        }
        getWindow().clearFlags(QvDeviceAbility.TYPE_SUPPORT_SET_MANUAL_ALARM_AUDIO);
        final MyDialog myDialog = new MyDialog(this);
        myDialog.setMessage(getString(R.string.key_upgrade_fail_hint));
        myDialog.setCanceledOnTouchOutside(false);
        myDialog.setPositiveClickListener(getString(R.string.yes), new MyDialog.onPositiveClickListener() { // from class: com.quvii.ubell.device.manage.view.f0
            @Override // com.quvii.ubell.publico.widget.MyDialog.onPositiveClickListener
            public final void onClick() {
                myDialog.dismiss();
            }
        });
        myDialog.show();
    }

    @Override // com.quvii.ubell.device.manage.contract.DMUpgradeContract.View
    public void showUpgradeHint() {
        final MyDialog myDialog = new MyDialog(this);
        myDialog.setMessage(getString(R.string.key_upgrade_info));
        myDialog.setPositiveClickListener(getString(R.string.yes), new MyDialog.onPositiveClickListener() { // from class: com.quvii.ubell.device.manage.view.d0
            @Override // com.quvii.ubell.publico.widget.MyDialog.onPositiveClickListener
            public final void onClick() {
                this.f9496a.lambda$showUpgradeHint$1(myDialog);
            }
        });
        myDialog.setNegativeClickListener(getString(R.string.no), new MyDialog.onNegativeClickListener() { // from class: com.quvii.ubell.device.manage.view.e0
            @Override // com.quvii.ubell.publico.widget.MyDialog.onNegativeClickListener
            public final void onClick() {
                myDialog.dismiss();
            }
        });
        myDialog.show();
    }

    @Override // com.quvii.ubell.device.manage.contract.DMUpgradeContract.View
    public void showUpgradeProcess(int i2) {
        if (!this.myHorizontalProgressBar.isShowing()) {
            this.myHorizontalProgressBar.show();
        }
        this.myHorizontalProgressBar.setCurrentProgress(i2);
    }

    @Override // com.quvii.ubell.device.manage.contract.DMUpgradeContract.View
    public void showUpgradeSuccess() {
        if (this.myHorizontalProgressBar.isShowing()) {
            this.myHorizontalProgressBar.dismiss();
        }
        getWindow().clearFlags(QvDeviceAbility.TYPE_SUPPORT_SET_MANUAL_ALARM_AUDIO);
        final MyDialog myDialog = new MyDialog(this);
        myDialog.setMessage(getString(R.string.key_upgrade_success_restart_hint));
        myDialog.setCanceledOnTouchOutside(false);
        myDialog.setPositiveClickListener(getString(R.string.yes), new MyDialog.onPositiveClickListener() { // from class: com.quvii.ubell.device.manage.view.g0
            @Override // com.quvii.ubell.publico.widget.MyDialog.onPositiveClickListener
            public final void onClick() {
                this.f9503a.lambda$showUpgradeSuccess$3(myDialog);
            }
        });
        myDialog.show();
    }
```

## guide

Guía paginada.

Origen: `com/quvii/ubell/main/view/StartGuideActivity.java:68-133`

```java
    public void initView(Bundle bundle) {
        this.mStrDes = new String[mIntDes.length];
        this.mDrawables = new Drawable[mIntDrawables.length];
        int i2 = 0;
        int i3 = 0;
        while (true) {
            String[] strArr = this.mStrDes;
            if (i3 >= strArr.length) {
                break;
            }
            strArr[i3] = getResources().getString(mIntDes[i3]);
            this.mDrawables[i3] = getResources().getDrawable(mIntDrawables[i3]);
            i3++;
        }
        ((ActivityStartGuideBinding) this.binding).vpIndicator.setNumber(mIntDes.length);
        ((ActivityStartGuideBinding) this.binding).tvDescribe.setTextColor(getResources().getColor(R.color.guide_blue_tv));
        ((ActivityStartGuideBinding) this.binding).tvDescribe.setText(this.mStrDes[0]);
        this.views = new ImageView[this.mDrawables.length];
        this.viewDatas = new ArrayList();
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(-1, -1);
        while (true) {
            ImageView[] imageViewArr = this.views;
            if (i2 >= imageViewArr.length) {
                MyPagerAdapter<View> myPagerAdapter = new MyPagerAdapter<>(this, this.viewDatas);
                this.adapter = myPagerAdapter;
                ((ActivityStartGuideBinding) this.binding).vp.setAdapter(myPagerAdapter);
                ((ActivityStartGuideBinding) this.binding).vp.addOnPageChangeListener(new ViewPager.j() { // from class: com.quvii.ubell.main.view.StartGuideActivity.1
                    @Override // androidx.viewpager.widget.ViewPager.j
                    public void onPageScrollStateChanged(int i4) {
                    }

                    @Override // androidx.viewpager.widget.ViewPager.j
                    public void onPageScrolled(int i4, float f2, int i5) {
                    }

                    @Override // androidx.viewpager.widget.ViewPager.j
                    public void onPageSelected(int i4) {
                        ((ActivityStartGuideBinding) ((BaseActivity) StartGuideActivity.this).binding).vpIndicator.refreshView(i4);
                        ((ActivityStartGuideBinding) ((BaseActivity) StartGuideActivity.this).binding).tvDescribe.setText(StartGuideActivity.this.mStrDes[i4]);
                    }
                });
                return;
            }
            imageViewArr[i2] = new ImageView(this);
            this.views[i2].setLayoutParams(layoutParams);
            this.views[i2].setImageDrawable(this.mDrawables[i2]);
            this.viewDatas.add(this.views[i2]);
            i2++;
        }
    }

    @Override // com.qing.mvpart.base.QvActivity, androidx.activity.ComponentActivity, android.app.Activity
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void setListener() {
        super.setListener();
        ((ActivityStartGuideBinding) this.binding).btStart.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.main.view.i1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9633a.lambda$setListener$0(view);
            }
        });
    }
```

## add_method_legacy

Métodos AP y audio.

Origen: `com/quvii/ubell/device/add/view/DAAddTypeActivity.java:14-57`

```java
    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$0(View view) {
        this.deviceAddInfo.setAddType(0);
        ((DAAddTypeContract.Presenter) getP()).connectDeviceAp(this.deviceAddInfo);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setListener$1(View view) {
        this.deviceAddInfo.setAddType(1);
        startActivity(DARouterInfoActivity.class);
    }

    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
        setTitlebar(getString(R.string.key_device_add));
        setRightBackBtn();
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void setListener() {
        super.setListener();
        ((ActivityDaAddTypeBinding) this.binding).btAddAp.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.device.add.view.a
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9384a.lambda$setListener$0(view);
            }
        });
        ((ActivityDaAddTypeBinding) this.binding).btAddVoice.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.device.add.view.b
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9386a.lambda$setListener$1(view);
            }
        });
    }

    @Override // com.quvii.ubell.device.add.contract.DAAddTypeContract.View
    public void showSwitchWifiFail() {
        startActivity(DANewHandleWifiActivity.class);
    }

    @Override // com.quvii.ubell.device.add.contract.DAAddTypeContract.View
    public void showSwitchWifiSuccess() {
        startActivity(DARouterInfoActivity.class);
    }
```

## router

Configuración y modos del router.

Origen: `com/quvii/ubell/device/add/view/DARouterInfoActivity.java:91-113`

```java
    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
        SoftHideKeyBoardUtil.assistActivity(this);
        setTitlebar(getString(R.string.key_wifi_setup));
        setRightBackBtn();
        RtlUtils.dealWithEdit(((ActivityDaRouterInfoBinding) this.binding).etPassword);
    }

    @Override // com.quvii.ubell.publico.base.TitlebarBaseDeviceAddActivity, com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void processLogic() {
        super.processLogic();
        int intExtra = getIntent().getIntExtra(AppConst.INTENT_DEVICE_WIFI_SET_TYPE, 1);
        if (intExtra == 0) {
            this.setMode = true;
        } else if (intExtra == 1) {
            this.setMode = false;
        }
        ((ActivityDaRouterInfoBinding) this.binding).tvNote.setText(this.setMode ? R.string.key_wifi_change_hint1 : R.string.key_router_choose_hint);
        ((ActivityDaRouterInfoBinding) this.binding).tvNote2.setVisibility(this.setMode ? 0 : 8);
        ((ActivityDaRouterInfoBinding) this.binding).btNext.setText(this.setMode ? R.string.key_change : R.string.key_next);
        ((DARouterInfoContract.Presenter) getP()).setDeviceAddInfo(this.deviceAddInfo);
        ((DARouterInfoContract.Presenter) getP()).searchWifi();
    }
```

## voice

Estados de audio.

Origen: `com/quvii/ubell/device/add/view/DAVoiceSetActivity.java:91-121`

```java
    @Override // com.quvii.ubell.device.add.contract.DAVoiceSetContract.View
    public void showConfigStatus(boolean z2, int i2) {
        if (z2) {
            StringBuilder sb = this.sbConfigInfo;
            sb.delete(0, sb.length());
        }
        if (i2 == 0) {
            this.sbConfigInfo.append(getString(R.string.key_device_config_status_send_data));
        } else if (i2 == 1) {
            StringBuilder sb2 = this.sbConfigInfo;
            sb2.append("\n");
            sb2.append(getString(R.string.key_device_add_wait_device_online));
        } else if (i2 == 2) {
            StringBuilder sb3 = this.sbConfigInfo;
            sb3.append("  ");
            sb3.append(getString(R.string.key_fail));
            ((ActivityDaVoiceSetBinding) this.binding).btNext.setText(R.string.key_retry);
        }
        ((ActivityDaVoiceSetBinding) this.binding).tvStatus.setText(this.sbConfigInfo.toString());
    }

    @Override // com.quvii.ubell.device.add.contract.DAVoiceSetContract.View
    public void showConfigSuccess(Class cls) {
        startActivity(cls);
        finish();
    }

    @Override // com.quvii.ubell.device.add.contract.DAVoiceSetContract.View
    public void showCountdownInfo(int i2) {
        ((ActivityDaVoiceSetBinding) this.binding).btNext.setText(String.valueOf(i2));
    }
```

## wifi_progress

Progreso, fallo y retry.

Origen: `com/quvii/ubell/device/add/view/DAWifiSetActivity.java:107-174`

```java
    @Override // com.quvii.ubell.device.add.contract.DAWifiSetContract.View
    public void showConfigStatus(boolean z2, int i2) {
        if (z2) {
            StringBuilder sb = this.sbConfigInfo;
            sb.delete(0, sb.length());
        }
        if (i2 == -2) {
            startActivity(DADeviceConfigFailActivity.class);
            finish();
        } else if (i2 == -1) {
            StringBuilder sb2 = this.sbConfigInfo;
            sb2.append("  ");
            sb2.append(getString(R.string.key_fail));
            resetNext();
        } else if (i2 == 1) {
            this.sbConfigInfo.append(getString(R.string.key_device_config_status_connect));
        } else if (i2 == 2) {
            StringBuilder sb3 = this.sbConfigInfo;
            sb3.append("\n");
            sb3.append(getString(R.string.key_device_config_status_send_data));
        } else if (i2 == 3) {
            StringBuilder sb4 = this.sbConfigInfo;
            sb4.append("\n");
            sb4.append(getString(R.string.key_device_add_wait_device_online));
        }
        ((ActivityDaWifiSetBinding) this.binding).tvStatus.setText(this.sbConfigInfo.toString());
    }

    @Override // com.quvii.ubell.device.add.contract.DAWifiSetContract.View
    public void showConfigSuccess(Class cls) {
        startActivity(cls);
        finish();
    }

    @Override // com.quvii.ubell.device.add.contract.DAWifiSetContract.View
    public void showCountdownInfo(int i2) {
        ((ActivityDaWifiSetBinding) this.binding).btNext.setText(String.valueOf(i2));
    }

    @Override // com.quvii.ubell.device.add.contract.DAWifiSetContract.View
    public void showNetworkError() {
        runOnUiThread(new Runnable() { // from class: com.quvii.ubell.device.add.view.t0
            @Override // java.lang.Runnable
            public final void run() {
                this.f9426a.lambda$showNetworkError$3();
            }
        });
    }

    @Override // com.quvii.ubell.device.add.contract.DAWifiSetContract.View
    public void showNoPermission() {
        new androidx.appcompat.app.c.a(this).setTitle(R.string.key_permission_title_permission_fail).setMessage(R.string.key_permission_message_permission_fail).setPositiveButton(R.string.key_setting, new DialogInterface.OnClickListener() { // from class: com.quvii.ubell.device.add.view.q0
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i2) {
                this.f9421a.lambda$showNoPermission$4(dialogInterface, i2);
            }
        }).setNegativeButton(R.string.key_cancel, new DialogInterface.OnClickListener() { // from class: com.quvii.ubell.device.add.view.r0
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i2) {
                DAWifiSetActivity.lambda$showNoPermission$5(dialogInterface, i2);
            }
        }).show();
    }

    @Override // com.quvii.ubell.device.add.contract.DAWifiSetContract.View
    public void showOrHideNetError(boolean z2) {
        ((ActivityDaWifiSetBinding) this.binding).tvNetError.setVisibility(z2 ? 0 : 4);
    }
```

## share_device

QR, enlace y expiración.

Origen: `com/quvii/ubell/share/view/ShareDeviceActivity.java:83-161`

```java
    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
        setTitlebar(getString(R.string.key_invite_user));
        ((ActivityShareDeviceBinding) this.binding).tvShareHint.setText(getString(R.string.key_share_scan_text));
    }

    @Override // com.quvii.ubell.publico.base.TitlebarBaseDeviceActivity, com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void processLogic() {
        super.processLogic();
        if (this.device != null) {
            ((ShareDeviceContract.Presenter) getP()).getShareInfo(this.device, getIntent().getStringExtra(AppConst.INTENT_URL));
        } else {
            DeviceShareInfo deviceShareInfo = (DeviceShareInfo) getIntent().getParcelableExtra("device_share_info");
            if (deviceShareInfo != null) {
                ((ShareDeviceContract.Presenter) getP()).getShareInfo(deviceShareInfo);
            }
        }
    }

    @Override // com.quvii.ubell.share.contract.ShareDeviceContract.View
    public void saveFileToPhoneAlbum(Activity activity, String str) {
        if (QvSystemUtil.IsAndroidQ()) {
            saveFiles(Collections.singletonList(str)).observeOn(AndroidSchedulers.mainThread()).subscribe(new MyObserver<Boolean>() { // from class: com.quvii.ubell.share.view.ShareDeviceActivity.1
                @Override // com.quvii.ubell.publico.base.MyObserver
                public void onMyNext(Boolean bool) {
                    super.onMyNext(bool);
                    LogUtil.i("save to phone album ret:" + bool);
                    ShareDeviceActivity.this.showSaveFileResult(bool.booleanValue());
                }
            });
        } else {
            new PasteTaskExecutor(activity, Collections.singletonList(str), false).start();
        }
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void setListener() {
        super.setListener();
        ((ActivityShareDeviceBinding) this.binding).ivLogo.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.share.view.i
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9795a.lambda$setListener$0(view);
            }
        });
        ((ActivityShareDeviceBinding) this.binding).btCopy.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.share.view.j
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9796a.lambda$setListener$1(view);
            }
        });
        ((ActivityShareDeviceBinding) this.binding).btQrCode.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.share.view.k
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9797a.lambda$setListener$2(view);
            }
        });
    }

    @Override // com.quvii.ubell.share.contract.ShareDeviceContract.View
    public void showQRCode(String str) {
        ((ActivityShareDeviceBinding) this.binding).ivQrCode.setImageBitmap(ImageUtil.createQRImage(str, ScreenUtil.dip2px(this.mContext, 120.0f), ScreenUtil.dip2px(this.mContext, 120.0f)));
        this.bitmap = ImageUtil.createQRImage(str, ScreenUtil.dip2px(this.mContext, 120.0f), ScreenUtil.dip2px(this.mContext, 120.0f));
    }

    public void showSaveFileResult(boolean z2) {
        showMessage(z2 ? R.string.key_save : R.string.key_save_fail);
    }

    @Override // com.quvii.ubell.share.contract.ShareDeviceContract.View
    public void showTextUrl(String str) {
        this.shareUrl = str;
        ((ActivityShareDeviceBinding) this.binding).tvUrl.setText(str);
    }

    @Override // com.quvii.ubell.share.contract.ShareDeviceContract.View
    public void showTimeHint(boolean z2, String str) {
        ((ActivityShareDeviceBinding) this.binding).tvTimeHint.setVisibility(z2 ? 0 : 8);
        ((ActivityShareDeviceBinding) this.binding).tvTimeHint.setText(str);
    }
```

## pairing_fail

Fallo de pairing.

Origen: `com/quvii/ubell/device/add/view/DADeviceConfigFailActivity.java:22-46`

```java
    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
        setTitlebar(getString(R.string.key_pairing_error), false);
    }

    @Override // com.qing.mvpart.base.QvActivity, androidx.activity.ComponentActivity, android.app.Activity
    public void onBackPressed() {
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void setListener() {
        super.setListener();
        ((ActivityDaDeviceConfigFailBinding) this.binding).btNext.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.device.add.view.r
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9422a.lambda$setListener$0(view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.quvii.ubell.publico.base.BaseActivity
    public ActivityDaDeviceConfigFailBinding getViewBinding() {
        return ActivityDaDeviceConfigFailBinding.inflate(getLayoutInflater());
    }
```

## share_manage

Lista y edición de usuarios.

Origen: `com/quvii/ubell/share/view/ShareManageActivity.java:85-151`

```java
    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
        setTitlebar(getString(R.string.key_user));
    }

    @Override // com.quvii.ubell.publico.base.TitlebarBaseDeviceActivity, com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void processLogic() {
        super.processLogic();
        ((ShareManageContract.Presenter) getP()).setDevice(this.device, this.deviceShareInfoList);
        this.shareDeviceAdapter = new ShareDeviceAdapter(this.mContext, this.deviceShareInfoList, this.device, new AnonymousClass1());
        ((ActivityShareManageBinding) this.binding).rvMain.setLayoutManager(new LinearLayoutManager(this.mContext));
        ((ActivityShareManageBinding) this.binding).rvMain.setAdapter(this.shareDeviceAdapter);
        ((ShareManageContract.Presenter) getP()).getShareInfo();
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void setListener() {
        super.setListener();
        setRightBtn(R.drawable.publico_selector_btn_add, new View.OnClickListener() { // from class: com.quvii.ubell.share.view.m
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9799a.lambda$setListener$0(view);
            }
        });
        ((ActivityShareManageBinding) this.binding).srlMain.setOnRefreshListener(new SwipeRefreshLayout.j() { // from class: com.quvii.ubell.share.view.n
            @Override // androidx.swiperefreshlayout.widget.SwipeRefreshLayout.j
            public final void onRefresh() {
                this.f9800a.lambda$setListener$1();
            }
        });
    }

    @Override // com.quvii.ubell.share.contract.ShareManageContract.View
    public void setRefresh(boolean z2) {
        ((ActivityShareManageBinding) this.binding).srlMain.setRefreshing(z2);
        ((ActivityShareManageBinding) this.binding).tvNoRecord.setVisibility(this.deviceShareInfoList.size() == 0 ? 0 : 8);
    }

    @Override // com.quvii.ubell.share.contract.ShareManageContract.View
    public void showDetail(final boolean z2, final DeviceShareInfo deviceShareInfo) {
        DeviceShareInfo deviceShareInfo2 = new DeviceShareInfo();
        deviceShareInfo2.setName(deviceShareInfo.getName());
        deviceShareInfo2.setPowers(deviceShareInfo.getPowers());
        final ShareDetailDialog shareDetailDialog = new ShareDetailDialog(this.mContext, deviceShareInfo2, this.device);
        shareDetailDialog.setClickListener(new ShareDetailDialog.OnClickListener() { // from class: com.quvii.ubell.share.view.o
            @Override // com.quvii.ubell.share.view.ShareDetailDialog.OnClickListener
            public final void onSave(DeviceShareInfo deviceShareInfo3) {
                this.f9801a.lambda$showDetail$2(shareDetailDialog, z2, deviceShareInfo, deviceShareInfo3);
            }
        });
        shareDetailDialog.show();
    }

    @Override // com.quvii.ubell.share.contract.ShareManageContract.View
    public void showShareInfo() {
        this.shareDeviceAdapter.notifyDataSetChanged();
        this.mTitlebar.getRightImageButton().setVisibility(this.deviceShareInfoList.size() < 100 ? 0 : 8);
        ((ActivityShareManageBinding) this.binding).tvNoRecord.setVisibility(this.deviceShareInfoList.size() == 0 ? 0 : 8);
    }

    @Override // com.quvii.ubell.share.contract.ShareManageContract.View
    public void showShareInfoCreate() {
        this.shareDeviceAdapter.notifyItemInserted(this.deviceShareInfoList.size() - 1);
        ((ActivityShareManageBinding) this.binding).rvMain.smoothScrollToPosition(this.deviceShareInfoList.size() - 1);
        this.mTitlebar.getRightImageButton().setVisibility(this.deviceShareInfoList.size() < 100 ? 0 : 8);
        ((ActivityShareManageBinding) this.binding).tvNoRecord.setVisibility(this.deviceShareInfoList.size() == 0 ? 0 : 8);
    }
```

## share_bind

Aceptar invitación.

Origen: `com/quvii/ubell/share/view/ShareBindActivity.java:38-88`

```java
    public void processLogic() {
        super.processLogic();
        DeviceShareInfo deviceShareInfo = (DeviceShareInfo) getIntent().getParcelableExtra("device_share_info");
        if (deviceShareInfo != null) {
            ((ShareBindContract.Presenter) getP()).setShareInfo(deviceShareInfo);
        } else {
            LogUtil.e("info is null!");
            finish();
        }
    }

    @Override // com.quvii.ubell.publico.base.BaseActivity, com.qing.mvpart.base.IActivity
    public void setListener() {
        super.setListener();
        ((ActivityShareBindBinding) this.binding).btAccept.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.share.view.a
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9787a.lambda$setListener$0(view);
            }
        });
        ((ActivityShareBindBinding) this.binding).llBackground.setOnClickListener(new View.OnClickListener() { // from class: com.quvii.ubell.share.view.b
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f9788a.lambda$setListener$1(view);
            }
        });
    }

    @Override // com.quvii.ubell.share.contract.ShareBindContract.View
    public void showBindInfo(String str, String str2, String str3) {
        ((ActivityShareBindBinding) this.binding).etUid.setText(str);
        ((ActivityShareBindBinding) this.binding).etDevicePassword.setText(str2);
        ((ActivityShareBindBinding) this.binding).etDeviceName.setText(str3);
        T t2 = this.binding;
        ((ActivityShareBindBinding) t2).etDeviceName.setSelection(((ActivityShareBindBinding) t2).etDeviceName.getText().toString().length());
    }

    @Override // com.quvii.ubell.share.contract.ShareBindContract.View
    public void showBindSuccess() {
        showMessage(R.string.key_config_success);
        backToMain();
    }

    @Override // com.quvii.ubell.share.contract.ShareBindContract.View
    public void showReAcceptInfo(int i2) {
        MyDialog myDialog = new MyDialog(this.mContext);
        myDialog.setTitle(i2);
        myDialog.setMessage(R.string.key_share_re_accept_hint);
        myDialog.setPositiveClickListener(R.string.key_confirm, new com.quvii.ubell.device.add.view.m(myDialog));
        myDialog.show();
    }
```

## tab_devices

Vacío y lista.

Origen: `com/quvii/ubell/main/view/MainDeviceListFragment.java:149-199`

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

## tab_events

Pestañas de eventos ocultas.

Origen: `com/quvii/ubell/main/view/MainMessageListFragment.java:229-284`

```java
    @Override // com.qing.mvpart.base.IActivity
    public void initView(Bundle bundle) {
        setTitlebar(getString(R.string.key_call_logs), false);
        ((FragmentMessageListBinding) this.binding).vpMain.setOffscreenPageLimit(2);
        ((FragmentMessageListBinding) this.binding).rgTitle.setVisibility(8);
        ((FragmentMessageListBinding) this.binding).llLineAllActivityRingsMotion.setVisibility(8);
        ((FragmentMessageListBinding) this.binding).ivDividingLine.setVisibility(8);
    }

    @Override // androidx.fragment.app.Fragment
    public void onHiddenChanged(boolean z2) {
        super.onHiddenChanged(z2);
        this.isHide = z2;
        if (z2) {
            return;
        }
        ((MainMessageListContract.Presenter) getP()).getMessageList(true);
        showWarningHint();
    }

    @Override // androidx.fragment.app.Fragment
    public void onResume() {
        super.onResume();
        if (this.isHide) {
            return;
        }
        ((MainMessageListContract.Presenter) getP()).getMessageList(true);
        if (this.dialog == null) {
            showWarningHint();
        }
    }

    @Override // com.qing.mvpart.base.IActivity
    public void processLogic() {
        int i2 = 0;
        if (getChildFragmentManager().r0().size() > 0) {
            while (i2 < getChildFragmentManager().r0().size()) {
                MainMessageTypeFragment mainMessageTypeFragment = (MainMessageTypeFragment) getChildFragmentManager().r0().get(i2);
                mainMessageTypeFragment.init(i2, this);
                this.mainMessageTypeFragments.add(mainMessageTypeFragment);
                i2++;
            }
        } else {
            int[] iArr = FRAGMENT_TYPE_LIST;
            int length = iArr.length;
            while (i2 < length) {
                int i3 = iArr[i2];
                MainMessageTypeFragment mainMessageTypeFragment2 = new MainMessageTypeFragment();
                mainMessageTypeFragment2.init(i3, this);
                this.mainMessageTypeFragments.add(mainMessageTypeFragment2);
                i2++;
            }
        }
        ((FragmentMessageListBinding) this.binding).vpMain.setAdapter(new AlarmInfoSlideFragmentAdapter(getChildFragmentManager(), this.mainMessageTypeFragments));
        ((MainMessageListContract.Presenter) getP()).setAlarmList(this.alarmInfoList);
    }
```

## tab_event_list

Vacío y lista de eventos.

Origen: `com/quvii/ubell/main/view/MainMessageTypeFragment.java:233-277`

```java
    public void showMessageView(List<AlarmInfo> list) {
        int i2;
        this.mAlarmInfoList.clear();
        int i3 = this.type;
        if (i3 != 1) {
            i2 = 2;
            if (i3 != 2) {
                i2 = -1;
            }
        } else {
            i2 = 19;
        }
        if (i2 == -1) {
            this.mAlarmInfoList.addAll(list);
        } else {
            for (AlarmInfo alarmInfo : list) {
                if (alarmInfo.getEvent() == i2) {
                    this.mAlarmInfoList.add(alarmInfo);
                }
            }
        }
        this.mAdapter.setData(this.mAlarmInfoList);
        ((FragmentMainMessageTypeBinding) this.binding).xfvMain.setPullLoadEnable(this.mAlarmInfoList.size() >= 15);
        updateMessageView();
    }

    public void stopRefresh() {
        T t2 = this.binding;
        if (((FragmentMainMessageTypeBinding) t2).xfvMain == null) {
            return;
        }
        ((FragmentMainMessageTypeBinding) t2).xfvMain.clearOffset();
        ((FragmentMainMessageTypeBinding) this.binding).xfvMain.stopRefresh();
        AppVariates.AlarmInfoLastRefreshTime = ((FragmentMainMessageTypeBinding) this.binding).xfvMain.getLastRefreshTime();
        ((FragmentMainMessageTypeBinding) this.binding).xfvMain.stopLoadMore();
    }

    public void updateMessageView() {
        if (this.mAdapter == null) {
            return;
        }
        List<AlarmInfo> list = this.mAlarmInfoList;
        setHintState(list == null || list.size() == 0);
        this.mAdapter.notifyDataSetChanged();
    }
```

## tab_gallery

Galería y edición.

Origen: `com/quvii/ubell/main/view/MainFileFragment.java:226-274`

```java
    @Override // com.quvii.ubell.main.contract.MainFileContract.View
    public void showDeleteView(boolean z2) {
        ((FragmentFlieBinding) this.binding).llEditInfo.setVisibility(z2 ? 0 : 8);
        MainTabContract.MainTabListener mainTabListener = this.mainTabListener;
        if (mainTabListener != null) {
            mainTabListener.setBottomVisible(!z2);
        }
    }

    @Override // com.quvii.ubell.main.contract.MainFileContract.View
    public void showPhotoView(final List<GridItem> list, Mode mode) {
        FileStickyGridAdapter fileStickyGridAdapter = this.adapter;
        if (fileStickyGridAdapter == null) {
            FileStickyGridAdapter fileStickyGridAdapter2 = new FileStickyGridAdapter(this.mContext, list, ((FragmentFlieBinding) this.binding).fileGrid, mode);
            this.adapter = fileStickyGridAdapter2;
            fileStickyGridAdapter2.setOnItemSelectorListener(new OnItemSelectorListener() { // from class: com.quvii.ubell.main.view.v
                @Override // com.quvii.ubell.main.adapter.OnItemSelectorListener
                public final void onItemSelected(int i2, Object obj) {
                    this.f9660a.lambda$showPhotoView$9(i2, obj);
                }
            });
            ((FragmentFlieBinding) this.binding).fileGrid.setAdapter((ListAdapter) this.adapter);
            ((FragmentFlieBinding) this.binding).fileGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() { // from class: com.quvii.ubell.main.view.m
                @Override // android.widget.AdapterView.OnItemClickListener
                public final void onItemClick(AdapterView adapterView, View view, int i2, long j2) {
                    this.f9641a.lambda$showPhotoView$10(list, adapterView, view, i2, j2);
                }
            });
        } else {
            fileStickyGridAdapter.clear();
            this.adapter.setList(list);
            FileStickyGridAdapter fileStickyGridAdapter3 = this.adapter;
            fileStickyGridAdapter3.isFirstEnter = true;
            fileStickyGridAdapter3.notifyDataSetChanged();
            setTitlebar(getString(R.string.key_album));
        }
        ((FragmentFlieBinding) this.binding).tvNoRecord.setVisibility(list.size() == 0 ? 0 : 8);
        ((FragmentFlieBinding) this.binding).fileGrid.setVisibility(list.size() != 0 ? 0 : 8);
    }

    @Override // com.quvii.ubell.main.contract.MainFileContract.View
    public void showSaveFileResult(boolean z2) {
        showMessage(z2 ? R.string.key_save : R.string.key_save_fail);
        this.adapter.getSelectedImage().clear();
        FileStickyGridAdapter fileStickyGridAdapter = this.adapter;
        fileStickyGridAdapter.isFirstEnter = true;
        fileStickyGridAdapter.notifyDataSetChanged();
        this.isSelectedAll = false;
    }
```

## tab_settings

Opciones y switches.

Origen: `com/quvii/ubell/main/view/MainMeFragment.java:127-213`

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
    }

    @Override // com.quvii.ubell.main.contract.MainMeContract.View
    public void showAutoRotateState(boolean z2) {
        ((FragmentMeBinding) this.binding).ivAutoRotate.setImageResource(z2 ? R.drawable.btn_switch_on : R.drawable.btn_switch_off);
    }

    @Override // com.quvii.ubell.main.contract.MainMeContract.View
    public void showChannelSwitchMode(int i2) {
        if (i2 != 1) {
            ((FragmentMeBinding) this.binding).tvChannelsSelectMode.setText(R.string.key_automatic);
        } else {
            ((FragmentMeBinding) this.binding).tvChannelsSelectMode.setText(R.string.key_by_hand);
        }
    }

    @Override // com.quvii.ubell.main.contract.MainMeContract.View
    public void showPreviewTimeState(int i2) {
        ((FragmentMeBinding) this.binding).tvPreviewTime.setText(i2 + "s");
    }

    @Override // com.quvii.ubell.main.contract.MainMeContract.View
    public void showSoundState(boolean z2) {
        ((FragmentMeBinding) this.binding).ivSound.setImageResource(z2 ? R.drawable.btn_switch_on : R.drawable.btn_switch_off);
    }

    @Override // com.quvii.ubell.main.contract.MainMeContract.View
    public void showVibrationState(boolean z2) {
        ((FragmentMeBinding) this.binding).ivVibration.setImageResource(z2 ? R.drawable.btn_switch_on : R.drawable.btn_switch_off);
    }
```

