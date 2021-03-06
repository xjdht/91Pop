package com.dante.ui.download;


import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.dante.R;
import com.dante.adapter.DownloadVideoAdapter;
import com.dante.custom.TastyToast;
import com.dante.data.DataManager;
import com.dante.data.model.UnLimit91PornItem;
import com.dante.service.DownloadVideoService;
import com.dante.ui.MvpFragment;
import com.dante.utils.DownloadManager;
import com.liulishuo.filedownloader.BaseDownloadTask;
import com.liulishuo.filedownloader.FileDownloadConnectListener;
import com.liulishuo.filedownloader.FileDownloader;
import com.liulishuo.filedownloader.model.FileDownloadStatus;
import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * A simple {@link Fragment} subclass.
 *
 * @author flymegoc
 */
public class DownloadingFragment extends MvpFragment<DownloadView, DownloadPresenter> implements DownloadManager.DownloadStatusUpdater, DownloadView {

    private static final String TAG = DownloadingFragment.class.getSimpleName();
    @Inject
    protected DataManager dataManager;
    @Inject
    protected DownloadPresenter downloadPresenter;
    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;
    Unbinder unbinder;
    private DownloadVideoAdapter mDownloadAdapter;
    private ArrayList<UnLimit91PornItem> mUnLimit91PornItemList;
    private FileDownloadConnectListener fileDownloadConnectListener = new FileDownloadConnectListener() {
        @Override
        public void connected() {
            Logger.t(TAG).d("连接上下载服务");
            List<UnLimit91PornItem> unLimit91PornItems = dataManager.loadDownloadingData();
            for (UnLimit91PornItem unLimit91PornItem : unLimit91PornItems) {
                int status = FileDownloader.getImpl().getStatus(unLimit91PornItem.getVideoResult().getVideoUrl(), unLimit91PornItem.getDownLoadPath(dataManager));
                Logger.t(TAG).d("fix status:::" + status);
                if (status != unLimit91PornItem.getStatus()) {
                    unLimit91PornItem.setStatus(status);
                    dataManager.updateUnLimit91PornItem(unLimit91PornItem);
                }
            }
            presenter.loadDownloadingData();
        }

        @Override
        public void disconnected() {
            Logger.t(TAG).d("下载服务连接断开了");
        }
    };

    @Inject
    public DownloadingFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DownloadManager.getImpl().addUpdater(this);
        FileDownloader.getImpl().addServiceConnectListener(fileDownloadConnectListener);
    }

    @NonNull
    @Override
    public DownloadPresenter createPresenter() {
        getActivityComponent().inject(this);
        return downloadPresenter;

    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        unbinder = ButterKnife.bind(this, view);
        mUnLimit91PornItemList = new ArrayList<>();
        mDownloadAdapter = new DownloadVideoAdapter(R.layout.item_unlimit_91porn_download, mUnLimit91PornItemList);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.getItemAnimator().setChangeDuration(0);
        recyclerView.setAdapter(mDownloadAdapter);
        mDownloadAdapter.setEmptyView(R.layout.empty_view, recyclerView);

        mDownloadAdapter.setOnItemChildClickListener(new BaseQuickAdapter.OnItemChildClickListener() {

            @Override
            public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
                UnLimit91PornItem unLimit91PornItem = (UnLimit91PornItem) adapter.getItem(position);
                if (unLimit91PornItem == null) {
                    return;
                }
                Logger.t(TAG).d("当前状态：" + unLimit91PornItem.getStatus());
                if (view.getId() == R.id.iv_download_control) {
                    if (FileDownloader.getImpl().isServiceConnected()) {
                        if (unLimit91PornItem.getStatus() == FileDownloadStatus.progress) {
                            FileDownloader.getImpl().pause(unLimit91PornItem.getDownloadId());
                            ((ImageView) view).setImageResource(R.drawable.start_download);
                        } else if (unLimit91PornItem.getStatus() == FileDownloadStatus.warn) {
                            startDownload(unLimit91PornItem, view, true);
                        } else if (unLimit91PornItem.getStatus() == FileDownloadStatus.paused) {
                            startDownload(unLimit91PornItem, view, false);
                        } else if (unLimit91PornItem.getStatus() == FileDownloadStatus.error) {
                            startDownload(unLimit91PornItem, view, false);
                        }
                    }
                }
            }
        });
        mDownloadAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                final UnLimit91PornItem unLimit91PornItem = (UnLimit91PornItem) adapter.getItem(position);
                if (unLimit91PornItem == null) return false;
                new AlertDialog.Builder(context).setMessage("刪除此任务？").setPositiveButton("好的", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        presenter.deleteDownloadingTask(unLimit91PornItem);
                        presenter.loadDownloadingData();
                    }
                }).show();
                return true;
            }
        });


    }

    @Override
    protected void onLazyLoadOnce() {
        super.onLazyLoadOnce();
        if (!FileDownloader.getImpl().isServiceConnected()) {
            FileDownloader.getImpl().bindService();
            Logger.t(TAG).d("启动下载服务");
        } else {
            presenter.loadDownloadingData();
            Logger.t(TAG).d("下载服务已经连接");
        }
    }

    private void startDownload(UnLimit91PornItem unLimit91PornItem, View view, boolean isForceReDownload) {
        boolean isDownloadNeedWifi = dataManager.isDownloadVideoNeedWifi();
        presenter.downloadVideo(unLimit91PornItem, isDownloadNeedWifi, isForceReDownload);
        ((ImageView) view).setImageResource(R.drawable.pause_download);
        Intent intent = new Intent(getContext(), DownloadVideoService.class);
        context.startService(intent);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        super.onCreateView(inflater, container, savedInstanceState);
        return inflater.inflate(R.layout.fragment_download, container, false);
    }

    @Override
    public void complete(BaseDownloadTask task) {
        if (mUnLimit91PornItemList == null || mUnLimit91PornItemList.size() == 0) {
            return;
        }
        Logger.t(TAG).d("已经下载完成了");
        UnLimit91PornItem unLimit91PornItem = presenter.findUnLimit91PornItemByDownloadId(task.getId());
        if (unLimit91PornItem != null) {
            int position = mUnLimit91PornItemList.indexOf(unLimit91PornItem);
            if (position >= 0 && position < mUnLimit91PornItemList.size()) {
                mUnLimit91PornItemList.remove(position);
                mDownloadAdapter.notifyItemRemoved(position);
            } else {
                presenter.loadDownloadingData();
            }
        } else {
            presenter.loadDownloadingData();
        }
    }

    @Override
    public void update(BaseDownloadTask task) {
        Logger.t(TAG).d("updateUnLimit91PornItem(BaseDownloadTask task)");
        if (mUnLimit91PornItemList == null || mUnLimit91PornItemList.size() == 0) {
            return;
        }
        UnLimit91PornItem unLimit91PornItem = presenter.findUnLimit91PornItemByDownloadId(task.getId());
        if (unLimit91PornItem != null) {
            int position = mUnLimit91PornItemList.indexOf(unLimit91PornItem);
            Logger.t(TAG).d("position" + position);
            if (position >= 0 && position < mUnLimit91PornItemList.size()) {
                mUnLimit91PornItemList.set(position, unLimit91PornItem);
                mDownloadAdapter.notifyItemChanged(position);
            } else {
                mUnLimit91PornItemList.add(unLimit91PornItem);
                mDownloadAdapter.notifyItemInserted(mUnLimit91PornItemList.size());
            }
        } else {
            presenter.loadDownloadingData();
        }
    }

    @Override
    public String getTitle() {
        return "正在下载";
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        FileDownloader.getImpl().removeServiceConnectListener(fileDownloadConnectListener);
        DownloadManager.getImpl().removeUpdater(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public void setDownloadingData(List<UnLimit91PornItem> unLimit91PornItems) {
        mUnLimit91PornItemList.clear();
        mUnLimit91PornItemList.addAll(unLimit91PornItems);
        mDownloadAdapter.notifyDataSetChanged();
        if (unLimit91PornItems.size() == 0) {
            try {
                Intent intent = new Intent(getContext(), DownloadVideoService.class);
                context.stopService(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setFinishedData(List<UnLimit91PornItem> unLimit91PornItems) {

    }

    @Override
    public void showLoading(boolean pullToRefresh) {

    }

    @Override
    public void showContent() {

    }

    @Override
    public void showMessage(String msg, int type) {
        super.showMessage(msg, type);
    }

    @Override
    public void showError(String message) {
        showMessage(message, TastyToast.ERROR);
    }
}
