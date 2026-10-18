package de.steffen.musikmathe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Vector controls with spoken labels and a compact toolbar layout. */
final class BrowserIcon extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean compact;
    final String kind;
    int count = 1;
    BrowserIcon(Context context, String kind, String label) {
        this(context, kind, label, false);
    }
    BrowserIcon(Context context, String kind, String label, boolean compact) {
        super(context); this.kind = kind; this.compact = compact; setContentDescription(label); setFocusable(true); setClickable(true);
        android.util.TypedValue value = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true);
        setBackgroundResource(value.resourceId);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float unit = compact ? Math.min(getWidth() / 48f, getHeight() / 32f) : Math.min(getWidth(), getHeight()) / 48f;
        // Compact controls retain readable glyphs; leave room above the video for its badge.
        float centerY = compact && kind.equals("video") && count > 0 ? 20 : 24;
        canvas.save(); canvas.translate(getWidth() / 2f - 24 * unit, getHeight() / 2f - centerY * unit); canvas.scale(unit, unit);
        paint.setColor(isEnabled() ? 0xff234a43 : 0xffb3c5be); paint.setStrokeWidth(2); paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND); paint.setStyle(Paint.Style.STROKE);
        switch (kind) {
            case "back": case "forward":
                if (kind.equals("forward")) { canvas.translate(48, 0); canvas.scale(-1, 1); }
                canvas.drawLine(15, 24, 33, 24, paint); canvas.drawLine(15, 24, 23, 16, paint); canvas.drawLine(15, 24, 23, 32, paint); break;
            case "reload":
                canvas.drawArc(new RectF(14,14,34,34),45,285,false,paint); canvas.drawLine(34,14,34,22,paint); canvas.drawLine(26,22,34,22,paint); break;
            case "stop": canvas.drawLine(17,17,31,31,paint); canvas.drawLine(31,17,17,31,paint); break;
            case "home":
                Path home = new Path(); home.moveTo(13,23); home.lineTo(24,13); home.lineTo(35,23); home.moveTo(16,21); home.lineTo(16,35); home.lineTo(32,35); home.lineTo(32,21); home.moveTo(21,35); home.lineTo(21,27); home.lineTo(27,27); home.lineTo(27,35); canvas.drawPath(home,paint); break;
            case "video":
                canvas.drawRoundRect(new RectF(11,14,37,34),4,4,paint);
                Path triangle=new Path();triangle.moveTo(21,19);triangle.lineTo(29,24);triangle.lineTo(21,29);triangle.close();canvas.drawPath(triangle,paint);
                if(count>0){paint.setStyle(Paint.Style.FILL);paint.setColor(0xff17685f);canvas.drawCircle(36,13,8,paint);paint.setColor(0xffffffff);paint.setTextSize(10);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(String.valueOf(count),36,17,paint);}break;
            case "tabs":
                canvas.drawRoundRect(new RectF(13,13,35,35),5,5,paint); paint.setStyle(Paint.Style.FILL); paint.setTextSize(13); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); paint.setTextAlign(Paint.Align.CENTER); canvas.drawText(String.valueOf(count),24,29,paint); break;
            case "plus": canvas.drawLine(15,24,33,24,paint); canvas.drawLine(24,15,24,33,paint); break;
            case "menu": paint.setStyle(Paint.Style.FILL); for(int y=16;y<=32;y+=8)canvas.drawCircle(24,y,2,paint); break;
            default: canvas.drawCircle(24,24,9,paint); canvas.drawLine(15,24,33,24,paint); canvas.drawOval(new RectF(20,15,28,33),paint); break;
        }
        canvas.restore();
    }
}
