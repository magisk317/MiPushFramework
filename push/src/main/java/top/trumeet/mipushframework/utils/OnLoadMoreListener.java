package top.trumeet.mipushframework.utils;

import android.util.Log;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public abstract class OnLoadMoreListener extends RecyclerView.OnScrollListener {

    private LinearLayoutManager layoutManager;
    private int itemCount;
    private int lastPosition;

    public abstract void onLoadMore();

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        if (needLoadMore(recyclerView)) {
            this.onLoadMore();
        };
    }

    private boolean needLoadMore(RecyclerView recyclerView) {
        if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
            layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();

            itemCount = layoutManager.getItemCount();
            lastPosition = layoutManager.findLastCompletelyVisibleItemPosition();
        } else {
            Log.e("OnLoadMoreListener", "The OnLoadMoreListener only support LinearLayoutManager");
            return false;
        }

        return lastPosition > itemCount - 3;
    }
}