package com.write.Quill;

import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;

import name.vbraun.view.write.Page;
import name.vbraun.view.write.TagOverlay;

import junit.framework.Assert;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;


public class ThumbnailAdapter extends BaseAdapter {
	private static final String TAG = "ThumbnailAdapter";

	protected static final int MIN_THUMBNAIL_WIDTH = 200;
	protected int thumbnail_width = MIN_THUMBNAIL_WIDTH;

	private Context context;

	
	public ThumbnailAdapter(Context c) {
		context = c;
    	computeItemHeights();
	}

    public int getCount() {
        return Bookshelf.getCurrentBook().filteredPagesSize();
    }

    public Object getItem(int position) {
        return null;
    }

    public long getItemId(int position) {
        return 0;
    }

    public void notifyTagsChanged() {
    	computeItemHeights();
    	notifyDataSetChanged();
    }
    
	protected Paint paint = new Paint();
	protected int[] heightOfItem = null;

    protected class Thumbnail extends View {
    	private static final String TAG = "Thumbnail";
    	protected int position;
    	protected Bitmap bitmap;
    	protected Page page;
    	protected TagOverlay tagOverlay = null;
    	protected boolean checked = false;
    	
		public Thumbnail(Context context) {
			super(context);	
		}

		public int heightOfRow() {
			int row = position / numColumns;
			int maxHeight = heightOfItem[row*numColumns];
			for (int i=row*numColumns+1; i<(row+1)*numColumns && i<heightOfItem.length; i++)
				maxHeight = Math.max(maxHeight, heightOfItem[i]);
			return maxHeight;
		}
		
		@Override
    	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			setMeasuredDimension(thumbnail_width, heightOfRow());
    	}
		
		@Override
		protected void onDraw(Canvas canvas) {
			if (bitmap == null) {
				canvas.drawColor(Color.DKGRAY);
				return;
			}
			float y = (getHeight()-bitmap.getHeight())/2;
			canvas.drawBitmap(bitmap, 0, y, paint);
	        Boolean checked = selectedPages.get(page);
	        if (checked != null && checked == true)
	        	canvas.drawARGB(0x50, 0, 0xff, 0);
		}
		
    }
    
    private int numColumns = 1;
    
    public void setNumColumns(int n) {
    	numColumns = n;
    }
    
    private void computeItemHeights() {
    	LinkedList<Page> pages = Bookshelf.getCurrentBook().filteredPages;
    	heightOfItem = new int[pages.size()];
    	ListIterator<Page> iter = pages.listIterator();
    	int pos = 0;
    	while (iter.hasNext()) 
    		heightOfItem[pos++] = (int)(thumbnail_width / iter.next().getAspectRatio());
    }
    	
    
    IdentityHashMap<Page, Boolean> selectedPages = new IdentityHashMap<Page, Boolean>();
    LinkedList<Thumbnail> unfinishedThumbnails = new LinkedList<Thumbnail>();
    
    protected boolean renderThumbnail() {
    	if (unfinishedThumbnails.isEmpty()) return false;
    	Thumbnail thumb = unfinishedThumbnails.pop();
		thumb.bitmap = thumb.page.renderBitmap(thumbnail_width, 2*thumbnail_width);
		Assert.assertTrue(thumb.bitmap != null);
		thumb.invalidate();
		return true;
    }
    
	@Override
    public View getView(int position, View convertView, ViewGroup parent) {
		Thumbnail thumb;
        if (convertView == null) {
            thumb = new Thumbnail(context);
        } else {
            thumb = (Thumbnail) convertView;
            if (thumb.position == position)
            	return thumb;
            if (thumb.bitmap != null)
            	thumb.bitmap.recycle();
        }
        Book book = Bookshelf.getCurrentBook();
        //  Log.d(TAG, "getView "+position+" "+book.filteredPagesSize());
        Page page = book.getFilteredPage(book.filteredPagesSize() - 1 - position);
        thumb.page = page;
        thumb.position = position;
        thumb.bitmap = null;
        thumb.tagOverlay = new TagOverlay(page.tags);
        thumb.requestLayout();     
        unfinishedThumbnails.add(thumb);
        ThumbnailView grid = (ThumbnailView)parent;
        grid.postIncrementalDraw();
		return thumb;
	}
	
    public void checkedStateChanged(int position, boolean checked) {
        Book book = Bookshelf.getCurrentBook();
    	Page page = book.getFilteredPage(book.filteredPagesSize() - 1 - position);
    	selectedPages.put(page, checked);
    }
	
    public void uncheckAll() {
    	selectedPages.clear();
    }

	
}