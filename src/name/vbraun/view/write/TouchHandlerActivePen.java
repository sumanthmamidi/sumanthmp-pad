package name.vbraun.view.write;

import junit.framework.Assert;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;

/**
 * Touch handler for an active pen (stylus with digitizer) that can distinguish stylus from finger touches
 * @author vbraun
 *
 */
public class TouchHandlerActivePen 
	extends TouchHandlerPenABC {
	private final static String TAG = "TouchHandlerActivePen";

	private int penID = -1;
	private int fingerId1 = -1;
	private int fingerId2 = -1;
	private float oldPressure, newPressure; 
	private float oldX, oldY, newX, newY;  // main pointer (usually pen)
	private float oldX1, oldY1, newX1, newY1;  // for 1st finger
	private float oldX2, oldY2, newX2, newY2;  // for 2nd finger
	private long oldT, newT;
	
	protected TouchHandlerActivePen(HandwriterView view) {
		super(view);
	}

	@Override
	protected void destroy() {
	}
	
	@Override
	protected void interrupt() {
		super.interrupt();
		penID = fingerId1 = fingerId2 = -1;
	}

	@Override
	protected boolean onTouchEvent(MotionEvent event) {
		int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_MOVE) {
			if (getMoveGestureWhileWriting() && fingerId1 != -1 && fingerId2 == -1) {
				int idx1 = event.findPointerIndex(fingerId1);
				if (idx1 != -1) {
					oldX1 = newX1 = event.getX(idx1);
					oldY1 = newY1 = event.getY(idx1);
				}
			}
			if (getMoveGestureWhileWriting() && fingerId2 != -1) {
				Assert.assertTrue(fingerId1 != -1);
				int idx1 = event.findPointerIndex(fingerId1);
				int idx2 = event.findPointerIndex(fingerId2);
				if (idx1 == -1 || idx2 == -1) return true;
				newX1 = event.getX(idx1);
				newY1 = event.getY(idx1);
				newX2 = event.getX(idx2);
				newY2 = event.getY(idx2);		
				view.invalidate();
				return true;
			}
			if (penID == -1 || N == 0) return true;
			int penIdx = event.findPointerIndex(penID);
			if (penIdx == -1) return true;
			
			oldT = newT;
			newT = System.currentTimeMillis();
			// Log.v(TAG, "ACTION_MOVE index="+pen+" pointerID="+penID);
			oldX = newX;
			oldY = newY;
			oldPressure = newPressure;
			newX = event.getX(penIdx);
			newY = event.getY(penIdx);
			newPressure = event.getPressure(penIdx);
			if (newT-oldT > 300) { // sometimes ACTION_UP is lost, why?
				Log.v(TAG, "Timeout in ACTION_MOVE, "+(newT-oldT));
				oldX = newX; oldY = newY;
				saveStroke();
				position_x[0] = newX;
				position_y[0] = newY;
				pressure[0] = newPressure;
				N = 1;
			}
			drawOutline(oldX, oldY, newX, newY, oldPressure, newPressure);
			
			int n = event.getHistorySize();
			if (N+n+1 >= Nmax) saveStroke();
			for (int i = 0; i < n; i++) {
				position_x[N+i] = event.getHistoricalX(penIdx, i);
				position_y[N+i] = event.getHistoricalY(penIdx, i);
				pressure[N+i] = event.getHistoricalPressure(penIdx, i);
			}
			position_x[N+n] = newX;
			position_y[N+n] = newY;
			pressure[N+n] = newPressure;
			N = N+n+1;
			return true;
		}		
		else if (action == MotionEvent.ACTION_DOWN) {
			Assert.assertTrue(event.getPointerCount() == 1);
			newT = System.currentTimeMillis();
			if (useForTouch(event) && getDoubleTapWhileWriting() && Math.abs(newT-oldT) < 250) {
				// double-tap
				view.centerAndFillScreen(event.getX(), event.getY());
				penID = fingerId1 = fingerId2 = -1;
				return true;
			}
			oldT = newT;
			if (useForTouch(event) && getMoveGestureWhileWriting() && event.getPointerCount()==1) {
				fingerId1 = event.getPointerId(0); 
				fingerId2 = -1;
				newX1 = oldX1 = event.getX(); 
				newY1 = oldY1 = event.getY();
			}
			if (penID != -1) {
				Log.e(TAG, "ACTION_DOWN without previous ACTION_UP");
				penID = -1;
				return true;
			}
			// Log.v(TAG, "ACTION_DOWN");
			if (!useForWriting(event)) 
				return true;   // eat non-pen events
			position_x[0] = newX = event.getX();
			position_y[0] = newY = event.getY();
			pressure[0] = newPressure = event.getPressure();
			N = 1;
			penID = event.getPointerId(0);
			initPenStyle();
			return true;
		}
		else if (action == MotionEvent.ACTION_UP) {
			Assert.assertTrue(event.getPointerCount() == 1);
			int id = event.getPointerId(0);
			if (id == penID) {
				// Log.v(TAG, "ACTION_UP: Got "+N+" points.");
				saveStroke();
				N = 0;
				view.callOnStrokeFinishedListener();
			} else if (getMoveGestureWhileWriting() && 
						(id == fingerId1 || id == fingerId2) &&
						fingerId1 != -1 && fingerId2 != -1) {
				Page page = getPage();
				float page_offset_x = page.transformation.offset_x;
				float page_offset_y = page.transformation.offset_y;
				float page_scale = page.transformation.scale;
				float scale = pinchZoomScaleFactor();
				float new_page_scale = page_scale * scale;
				// clamp scale factor
				float W = view.canvas.getWidth();
				float H = view.canvas.getHeight();
				float max_WH = Math.max(W, H);
				float min_WH = Math.min(W, H);
				new_page_scale = Math.min(new_page_scale, 5*max_WH);
				new_page_scale = Math.max(new_page_scale, 0.4f*min_WH);
				scale = new_page_scale / page_scale;
				// compute offset
				float x0 = (oldX1 + oldX2)/2;
				float y0 = (oldY1 + oldY2)/2;
				float x1 = (newX1 + newX2)/2;
				float y1 = (newY1 + newY2)/2;
				float new_offset_x = page_offset_x*scale-x0*scale+x1;
				float new_offset_y = page_offset_y*scale-y0*scale+y1;
				// perform pinch-to-zoom here
				page.setTransform(new_offset_x, new_offset_y, new_page_scale, view.canvas);
				page.draw(view.canvas);
				view.invalidate();
			}
			penID = fingerId1 = fingerId2 = -1;
			return true;
		}
		else if (action == MotionEvent.ACTION_CANCEL) {
			// e.g. you start with finger and use pen
			// if (event.getPointerId(0) != penID) return true;
			Log.v(TAG, "ACTION_CANCEL");
			N = 0;
			penID = fingerId1 = fingerId2 = -1;
			getPage().draw(view.canvas);
			view.invalidate();
			return true;
		}
		else if (action == MotionEvent.ACTION_POINTER_DOWN) {  // start move gesture
			if (fingerId1 == -1) return true; // ignore after move finished
			if (fingerId2 != -1) return true; // ignore more than 2 fingers
			int idx2 = event.getActionIndex();
			oldX2 = newX2 = event.getX(idx2);
			oldY2 = newY2 = event.getY(idx2);
			float dx = newX2-newX1;
			float dy = newY2-newY1;
			float distance = FloatMath.sqrt(dx*dx+dy*dy);
			if (distance >= getMoveGestureMinDistance()) {
				fingerId2 = event.getPointerId(idx2);
			}
			// Log.v(TAG, "ACTION_POINTER_DOWN "+fingerId2+" + "+fingerId1+" "+oldX1+" "+oldY1+" "+oldX2+" "+oldY2);
		}
		return false;
	}

	private RectF mRectF = new RectF();
	private Rect  mRect  = new Rect();
	
	@Override
	protected void onDraw(Canvas canvas, Bitmap bitmap) {
		if (fingerId2 != -1) {
			canvas.drawARGB(0xff, 0xaa, 0xaa, 0xaa);
			float W = canvas.getWidth();
			float H = canvas.getHeight();
			float scale = pinchZoomScaleFactor();
			float x0 = (oldX1 + oldX2)/2;
			float y0 = (oldY1 + oldY2)/2;
			float x1 = (newX1 + newX2)/2;
			float y1 = (newY1 + newY2)/2;
			mRectF.set(-x0*scale+x1, -y0*scale+y1, (-x0+W)*scale+x1, (-y0+H)*scale+y1);
			mRect.set(0, 0, canvas.getWidth(), canvas.getHeight());
			canvas.drawBitmap(bitmap, mRect, mRectF, (Paint)null);
		} else
			canvas.drawBitmap(bitmap, 0, 0, null);
	}
	
	private float pinchZoomScaleFactor() {
		if (view.getMoveGestureFixZoom())
			return 1f;
		float dx, dy;
		dx = oldX1-oldX2;
		dy = oldY1-oldY2;
		float old_distance = FloatMath.sqrt(dx*dx + dy*dy);
		if (old_distance < 10) {
			// Log.d("TAG", "old_distance too small "+old_distance);
			return 1;
		}
		dx = newX1-newX2;
		dy = newY1-newY2;
		float new_distance = FloatMath.sqrt(dx*dx + dy*dy);
		float scale = new_distance / old_distance;
		if (scale < 0.1f || scale > 10f) {
			// Log.d("TAG", "ratio out of bounds "+new_distance);
			return 1f;
		}
		return scale;
	}
	


}
