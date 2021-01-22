package com.isoftstone.circleimageview;

import ohos.agp.components.AttrSet;
import ohos.agp.components.Component;
import ohos.agp.components.Image;
import ohos.agp.components.element.Element;
import ohos.agp.render.*;
import ohos.agp.utils.Color;
import ohos.agp.utils.Matrix;
import ohos.agp.utils.RectFloat;
import ohos.app.Context;
import ohos.hiviewdfx.HiLog;
import ohos.hiviewdfx.HiLogLabel;
import ohos.media.image.ImageSource;
import ohos.media.image.PixelMap;
import ohos.media.image.common.PixelFormat;
import ohos.media.image.common.Rect;
import ohos.media.image.common.Size;
import ohos.multimodalinput.event.MmiPoint;
import ohos.multimodalinput.event.TouchEvent;

import java.io.InputStream;

public class CircleImageView extends Image implements Image.DrawTask, Image.TouchEventListener,Image.LayoutRefreshedListener{
    private static HiLogLabel label = new HiLogLabel(HiLog.LOG_APP, 0x000110, "CircleImageView");
    private static final ScaleMode SCALE_MODE = ScaleMode.CLIP_CENTER;
    private PixelMap mPixelMap;
    private final RectFloat mDrawableRect = new RectFloat();
    private final RectFloat mBorderRect = new RectFloat();
    private final Matrix mShaderMatrix = new Matrix();
    //图片画笔
    private final Paint mPixelMapPaint = new Paint();
    //边界画笔
    private final Paint mBorderPaint = new Paint();
    //圆环背景画笔
    private final Paint mCircleBackgroundPaint = new Paint();
    private PixelMapShader mPixelMapShader = new PixelMapShader();
    private static final int DEFAULT_BORDER_WIDTH = 10;
    private static final Color DEFAULT_BORDER_COLOR = Color.BLACK;
    private static final Color DEFAULT_CIRCLE_BACKGROUND_COLOR = Color.TRANSPARENT;
    private static final float DEFAULT_IMAGE_ALPHA = 1.0f;
    private static final boolean DEFAULT_BORDER_OVERLAY = false;
    //边框颜色 civ_border_color
    private Color mBorderColor = DEFAULT_BORDER_COLOR;
    //边框宽度 civ_border_width
    private int mBorderWidth = DEFAULT_BORDER_WIDTH;
    //背景圆颜色  civ_circle_background_color
    private Color mCircleBackgroundColor = DEFAULT_CIRCLE_BACKGROUND_COLOR;
    //是否允许圆图压住圆环 civ_border_overlay
    private boolean mBorderOverlay;
    private float mImageAlpha = DEFAULT_IMAGE_ALPHA;
    private ColorFilter mColorFilter;
    private boolean mRebuildShader;
    private float mDrawableRadius;
    private float mBorderRadius;
    private boolean mDisableCircularTransformation;
    //   private Canvas mBitmapCanvas;
    private boolean mInitialized;
    private PixelMapHolder pixelMapHolder;//像素图片持有者

    public CircleImageView(Context context) {
        super(context);
        HiLog.info(label, "Log_单参构造");
        init();
        //设置TouchEvent监听
        setTouchEventListener(this::onTouchEvent);
        //设置LayoutRefreshed监听
        setLayoutRefreshedListener(this::onRefreshed);
    }

    public CircleImageView(Context context, AttrSet attrSet) {
        this(context, attrSet, null);
        HiLog.info(label, "Log_xml构造");
    }

    public CircleImageView(Context context, AttrSet attrSet, String styleName) {
        super(context, attrSet, styleName);
        //获取xml中设置的自定义属性值
        mBorderWidth = attrSet.getAttr("civ_border_width").get().getIntegerValue();
        mBorderColor = attrSet.getAttr("civ_border_color").get().getColorValue();
        mBorderOverlay = attrSet.getAttr("civ_border_overlay").get().getBoolValue();
        mCircleBackgroundColor = attrSet.getAttr("civ_circle_background_color").get().getColorValue();

        init();
        //设置TouchEvent监听
        setTouchEventListener(this::onTouchEvent);
        //设置Refreshed监听
        setLayoutRefreshedListener(this::onRefreshed);
    }

    @Override
    public void invalidate() {
        //添加绘制任务
        addDrawTask(this::onDraw);
    }

    @Override
    public void onDraw(Component component, Canvas canvas) {
        //绘制背景圆
        if (mCircleBackgroundColor != Color.TRANSPARENT) {
            canvas.drawCircle(mDrawableRect.getHorizontalCenter(), mDrawableRect.getVerticalCenter(), mDrawableRadius, mCircleBackgroundPaint);
        }
        //绘制边框
        if (mBorderWidth > 0) {
            canvas.drawCircle(mBorderRect.getHorizontalCenter(), mBorderRect.getVerticalCenter(), mBorderRadius, mBorderPaint);
        }
        if (pixelMapHolder == null) {
            return;
        }
        synchronized (pixelMapHolder) {
            // 绘制图片，使用之前计算的数据
            if (mRebuildShader) {
                mRebuildShader = false;
                PixelMapShader bitmapShader = new PixelMapShader(pixelMapHolder, Shader.TileMode.CLAMP_TILEMODE, Shader.TileMode.CLAMP_TILEMODE);
                bitmapShader.setShaderMatrix(mShaderMatrix);
                mPixelMapPaint.setShader(bitmapShader, Paint.ShaderType.PIXELMAP_SHADER);
            }
            HiLog.info(label, "Log_圆图绘制");
            canvas.drawCircle(mDrawableRect.getHorizontalCenter(), mDrawableRect.getVerticalCenter(), mDrawableRadius, mPixelMapPaint);
            pixelMapHolder = null;
        }
    }

    @Override
    public boolean onTouchEvent(Component component, TouchEvent touchEvent) {
        HiLog.info(label, "Log_onTouchEvent");
        if (mDisableCircularTransformation) {
         return super.getTouchEventListener().onTouchEvent(component, touchEvent);
        }
        //获取点信息
        MmiPoint point = touchEvent.getPointerPosition(touchEvent.getIndex());
        return inTouchableArea(point) && super.getTouchEventListener().onTouchEvent(component, touchEvent);
    }

    @Override
    public void onRefreshed(Component component) {
        HiLog.info(label, "Log_onRefreshed");
        updateDimensions();
        invalidate();
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        updateDimensions();
        invalidate();
    }

    @Override
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        super.setPaddingRelative(start, top, end, bottom);
        updateDimensions();
        invalidate();
    }

    //由于有缩放所以需要计算是否在缩放后的范围内
    private boolean inTouchableArea(MmiPoint point) {
        if (mBorderRect.isEmpty()) {
            return true;
        }

        return Math.pow(point.getX() - mBorderRect.getHorizontalCenter(), 2) + Math.pow(point.getY() - mBorderRect.getVerticalCenter(), 2) <= Math.pow(mBorderRadius, 2);
    }

    //三个画笔设置
    private void init() {
        mInitialized = true;
        HiLog.info(label, "Log_init");
        super.setScaleMode(SCALE_MODE);

        mPixelMapPaint.setAntiAlias(true);
        mPixelMapPaint.setDither(true);
        mPixelMapPaint.setFilterBitmap(true);
        mPixelMapPaint.setAlpha(mImageAlpha);
        mPixelMapPaint.setColorFilter(mColorFilter);

        mBorderPaint.setStyle(Paint.Style.STROKE_STYLE);
        mBorderPaint.setAntiAlias(true);
        mBorderPaint.setColor(mBorderColor);
        mBorderPaint.setStrokeWidth(mBorderWidth);

        mCircleBackgroundPaint.setStyle(Paint.Style.FILL_STYLE);
        mCircleBackgroundPaint.setAntiAlias(true);
        mCircleBackgroundPaint.setColor(mCircleBackgroundColor);
    }

    /**
     * 获取原有Image中的位图资源后重新设置PixelMapHolder
     */
    private void initializePixelMap() {
        HiLog.info(label, "initializePixelMap");
        if (mPixelMap != null) {
            pixelMapHolder = new PixelMapHolder(mPixelMap);
            if (mInitialized) {
                updateShaderMatrix();
            }
            invalidate();//重新检验该组件
        } else {
            HiLog.info(label, "Log_mPixelMapNULL");
            pixelMapHolder = null;
        }
    }



    /**
     * 通过资源ID获取位图对象
     **/
    private PixelMap getPixelMap(int resId) {
        InputStream drawableInputStream = null;
        try {
            drawableInputStream = getResourceManager().getResource(resId);
            ImageSource.SourceOptions sourceOptions = new ImageSource.SourceOptions();
            sourceOptions.formatHint = "image/png";
            ImageSource imageSource = ImageSource.create(drawableInputStream, null);
            ImageSource.DecodingOptions decodingOptions = new ImageSource.DecodingOptions();
            decodingOptions.desiredSize = new Size(0, 0);
            decodingOptions.desiredRegion = new Rect(0, 0, 0, 0);
            decodingOptions.desiredPixelFormat = PixelFormat.ARGB_8888;
            PixelMap pixelMap = imageSource.createPixelmap(decodingOptions);
            return pixelMap;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (drawableInputStream != null) {
                    drawableInputStream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }


    //在设置图片时获取图片资源解码为无压缩的位图格式
    @Override
    public void setPixelMap(PixelMap pixelMap) {
        // super.setPixelMap(pixelMap);
        HiLog.info(label, "Log_setPixelMap");
        mPixelMap = pixelMap;
        initializePixelMap();
    }

    @Override
    public void setImageAndDecodeBounds(int resId) {
        // super.setImageAndDecodeBounds(resId);
        mPixelMap = getPixelMap(resId);
        initializePixelMap();
        HiLog.info(label, "Log_setImageAndDecodeBounds");
    }

    @Override
    public void setImageElement(Element element) {
        //  super.setImageElement(element);
        //TODO
        mPixelMap = getPixelMap();
        initializePixelMap();
        HiLog.info(label, "Log_setImageElement");
    }

    //布局变化时重新计算画布范围
    private void updateDimensions() {
        HiLog.info(label, "Log_updateDimensions");
        mBorderRect.modify(calculateBounds());
        mBorderRadius = Math.min((mBorderRect.getHeight() - mBorderWidth) / 2.0f, (mBorderRect.getWidth() - mBorderWidth) / 2.0f);
        mDrawableRect.modify(mBorderRect);
        //图片不能压住圆环并且边框宽度不为0则将图片缩小
        if (!mBorderOverlay && mBorderWidth > 0) {
            HiLog.info(label, "Log_shouldinSet");
            mDrawableRect.shrink(mBorderWidth - 1.0f, mBorderWidth - 1.0f);
        }
        mDrawableRadius = Math.min(mDrawableRect.getHeight() / 2.0f, mDrawableRect.getWidth() / 2.0f);
        updateShaderMatrix();
    }

    private RectFloat calculateBounds() {
        int availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int availableHeight = getHeight() - getPaddingTop() - getPaddingBottom();

        int sideLength = Math.min(availableWidth, availableHeight);

        float left = getPaddingLeft() + (availableWidth - sideLength) / 2f;
        float top = getPaddingTop() + (availableHeight - sideLength) / 2f;

        return new RectFloat(left, top, left + sideLength, top + sideLength);
    }

    public Color getBorderColor() {
        return mBorderColor;
    }

    public void setBorderColor(Color borderColor) {
        HiLog.info(label,"Log_setBorderColor");
        if (borderColor == mBorderColor) {
            return;
        }

        mBorderColor = borderColor;
        mBorderPaint.setColor(borderColor);
        invalidate();
    }

    public Color getCircleBackgroundColor() {
        return mCircleBackgroundColor;
    }

    public void setCircleBackgroundColor( Color circleBackgroundColor) {
        if (circleBackgroundColor == mCircleBackgroundColor) {
            return;
        }

        mCircleBackgroundColor = circleBackgroundColor;
        mCircleBackgroundPaint.setColor(circleBackgroundColor);
        invalidate();
    }

    public int getBorderWidth() {
        return mBorderWidth;
    }

    public void setBorderWidth(int borderWidth) {
        if (borderWidth == mBorderWidth) {
            return;
        }

        mBorderWidth = borderWidth;
        mBorderPaint.setStrokeWidth(borderWidth);
        updateDimensions();
        invalidate();
    }

    public boolean isBorderOverlay() {
        return mBorderOverlay;
    }

    public void setBorderOverlay(boolean borderOverlay) {
        if (borderOverlay == mBorderOverlay) {
            return;
        }
        mBorderOverlay = borderOverlay;
        updateDimensions();
        invalidate();
    }

    public boolean isDisableCircularTransformation() {
        return mDisableCircularTransformation;
    }

    public void setDisableCircularTransformation(boolean disableCircularTransformation) {
        if (disableCircularTransformation == mDisableCircularTransformation) {
            return;
        }

        mDisableCircularTransformation = disableCircularTransformation;

        if (disableCircularTransformation) {
            mPixelMap = null;
            //  mBitmapCanvas = null;
            mPixelMapPaint.setShader(null,null);
        } else {
            initializePixelMap();
        }

        invalidate();
    }


    @Override
    public float getAlpha() {
        return mImageAlpha;
    }

    @Override
    public void setAlpha(float alpha) {

        if (alpha == mImageAlpha) {
            return;
        }

        mImageAlpha = alpha;

        // This might be called during ImageView construction before
        // member initialization has finished on API level >= 16.
        if (mInitialized) {
            mPixelMapPaint.setAlpha(alpha);
            invalidate();
        }
        super.setAlpha(alpha);
    }

    public void setColorFilter(ColorFilter cf) {
        if (cf == mColorFilter) {
            return;
        }

        mColorFilter = cf;

        if (mInitialized) {
            mPixelMapPaint.setColorFilter(cf);
            invalidate();
        }
    }


    public ColorFilter getColorFilter() {
        return mColorFilter;
    }


    @Override
    public ScaledListener getScaledListener() {
        HiLog.info(label, "Log_getScaledListener");
        updateDimensions();
        invalidate();//重新检验该组件
        return super.getScaledListener();
    }

    @Override
    public LayoutRefreshedListener getLayoutRefreshedListener() {
        HiLog.info(label, "Log_getLayoutRefreshedListener");
        updateDimensions();
        invalidate();//重新检验该组件
        return super.getLayoutRefreshedListener();
    }

    /**
     * 这个函数为获取pixelMapShader的Matrix参数，设置最小缩放比例，平移参数。
     * 作用：保证图片损失度最小和始终绘制图片正中央的那部分
     */
    private void updateShaderMatrix() {
        //    mPixelMap = getPixelMap();
        if (mPixelMap == null) {
            HiLog.info(label, "Log_mPixelMapNULL");
            return;
        }

        float scale;
        float dx = 0;
        float dy = 0;

        mShaderMatrix.setMatrix(null);

        int pixelMapHeight = mPixelMap.getImageInfo().size.height;
        int pixelMapWidth = mPixelMap.getImageInfo().size.height;

        if (pixelMapWidth * mDrawableRect.getHeight() > mDrawableRect.getWidth() * pixelMapHeight) {
            scale = mDrawableRect.getHeight() / (float) pixelMapHeight;
            dx = (mDrawableRect.getWidth() - pixelMapWidth * scale) * 0.5f;
        } else {
            scale = mDrawableRect.getWidth() / (float) pixelMapWidth;
            dy = (mDrawableRect.getHeight() - pixelMapHeight * scale) * 0.5f;
        }

        mShaderMatrix.setScale(scale, scale);
        mShaderMatrix.postTranslate((int) (dx + 0.5f) + mDrawableRect.left, (int) (dy + 0.5f) + mDrawableRect.top);
        HiLog.info(label, "Log_mPixelMapEND");
        mRebuildShader = true;
    }

}
