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
    int tint;
    int count = 1;
    BrowserIcon(Context context, String kind, String label) {
        this(context, kind, label, false);
    }
    BrowserIcon(Context context, String kind, String label, boolean compact) {
        super(context); tint=context.getColor(R.color.browser_ink); this.kind = kind; this.compact = compact; setContentDescription(label); setFocusable(true); setClickable(true);
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
        paint.setColor(isEnabled() ? tint : getContext().getColor(R.color.browser_disabled)); paint.setStrokeWidth(2); paint.setStrokeCap(Paint.Cap.ROUND);
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
            case "star": case "star-filled": case "bookmarks":
                Path star=new Path();for(int i=0;i<10;i++){double angle=-Math.PI/2+i*Math.PI/5;float radius=i%2==0?12:5.5f;float x=24+(float)Math.cos(angle)*radius,y=24+(float)Math.sin(angle)*radius;if(i==0)star.moveTo(x,y);else star.lineTo(x,y);}star.close();if(kind.equals("star-filled"))paint.setStyle(Paint.Style.FILL);canvas.drawPath(star,paint);break;
            case "private":
                Path hat=new Path();hat.moveTo(15,21);hat.lineTo(19,12);hat.lineTo(29,12);hat.lineTo(33,21);hat.close();canvas.drawPath(hat,paint);canvas.drawLine(11,22,37,22,paint);canvas.drawCircle(17,31,5,paint);canvas.drawCircle(31,31,5,paint);canvas.drawLine(22,30,26,30,paint);break;
            case "switch-tabs":
                canvas.drawRoundRect(new RectF(17,17,36,36),3,3,paint);Path behind=new Path();behind.moveTo(30,12);behind.lineTo(12,12);behind.lineTo(12,30);canvas.drawPath(behind,paint);break;
            case "history":
                canvas.drawArc(new RectF(13,13,35,35),-70,310,false,paint);canvas.drawLine(12,14,12,21,paint);canvas.drawLine(12,21,19,21,paint);canvas.drawLine(24,17,24,24,paint);canvas.drawLine(24,24,29,27,paint);break;
            case "download":
                canvas.drawLine(24,12,24,29,paint);canvas.drawLine(18,23,24,29,paint);canvas.drawLine(24,29,30,23,paint);canvas.drawLine(14,31,14,36,paint);canvas.drawLine(14,36,34,36,paint);canvas.drawLine(34,36,34,31,paint);break;
            case "share":
                canvas.drawLine(18,22,30,15,paint);canvas.drawLine(18,26,30,33,paint);canvas.drawCircle(14,24,4,paint);canvas.drawCircle(34,13,4,paint);canvas.drawCircle(34,35,4,paint);break;
            case "copy":
                canvas.drawRoundRect(new RectF(18,17,35,36),2,2,paint);Path copy=new Path();copy.moveTo(29,13);copy.lineTo(13,13);copy.lineTo(13,30);canvas.drawPath(copy,paint);break;
            case "find":
                canvas.drawCircle(21,21,8,paint);canvas.drawLine(27,27,36,36,paint);break;
            case "desktop":
                canvas.drawRoundRect(new RectF(11,13,37,30),2,2,paint);canvas.drawLine(24,30,24,36,paint);canvas.drawLine(18,36,30,36,paint);break;
            case "cast":
                Path tv=new Path();tv.moveTo(12,21);tv.lineTo(12,13);tv.lineTo(37,13);tv.lineTo(37,34);tv.lineTo(28,34);canvas.drawPath(tv,paint);
                canvas.drawArc(new RectF(-2,21,24,47),-90,90,false,paint);canvas.drawArc(new RectF(3,26,19,42),-90,90,false,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(11,34,2,paint);break;
            case "fullscreen":
                Path corners=new Path();corners.moveTo(20,13);corners.lineTo(13,13);corners.lineTo(13,20);corners.moveTo(28,13);corners.lineTo(35,13);corners.lineTo(35,20);corners.moveTo(35,28);corners.lineTo(35,35);corners.lineTo(28,35);corners.moveTo(20,35);corners.lineTo(13,35);corners.lineTo(13,28);canvas.drawPath(corners,paint);break;
            case "pip":
                canvas.drawRoundRect(new RectF(11,13,37,35),2,2,paint);paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(new RectF(24,24,34,32),1,1,paint);break;
            case "shield":
                Path shield=new Path();shield.moveTo(24,11);shield.lineTo(35,16);shield.lineTo(34,27);shield.quadTo(33,33,24,38);shield.quadTo(15,33,14,27);shield.lineTo(13,16);shield.close();canvas.drawPath(shield,paint);canvas.drawLine(19,24,23,28,paint);canvas.drawLine(23,28,30,20,paint);break;
            case "key": case "vpn":
                canvas.drawCircle(18,21,7,paint);canvas.drawLine(23,26,35,37,paint);canvas.drawLine(30,32,34,28,paint);canvas.drawLine(33,35,37,31,paint);break;
            case "settings":
                Path gear=new Path();for(int i=0;i<32;i++){double angle=i*Math.PI/16;float radius=(i%4==0||i%4==3)?13:10;float x=24+(float)Math.cos(angle)*radius,y=24+(float)Math.sin(angle)*radius;if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();canvas.drawPath(gear,paint);canvas.drawCircle(24,24,4,paint);break;
            case "night":
                Path moon=new Path();moon.moveTo(29,12);moon.cubicTo(14,10,9,28,21,35);moon.cubicTo(28,39,36,34,37,27);moon.cubicTo(26,32,19,20,29,12);moon.close();canvas.drawPath(moon,paint);break;
            case "close": canvas.drawLine(16,16,32,32,paint);canvas.drawLine(16,32,32,16,paint);break;
            default: canvas.drawCircle(24,24,9,paint); canvas.drawLine(15,24,33,24,paint); canvas.drawOval(new RectF(20,15,28,33),paint); break;
        }
        canvas.restore();
    }
}
