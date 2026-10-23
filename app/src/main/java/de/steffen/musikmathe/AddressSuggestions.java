package de.steffen.musikmathe;

import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A non-modal local dropdown: typing alone never changes the page or the typed text. */
final class AddressSuggestions {
    private final EditText address;
    private final BrowserStore store;
    private final View anchor;
    private final Consumer<String> navigate;
    private final ListPopupWindow popup;
    private final Rows adapter=new Rows();
    private List<BrowserStore.Entry> rows=new ArrayList<>();
    private int selection=-1;
    private boolean destroyed;
    private final TextWatcher changes=new TextWatcher(){
        public void beforeTextChanged(CharSequence s,int start,int count,int after){}
        public void onTextChanged(CharSequence s,int start,int before,int count){refresh();}
        public void afterTextChanged(Editable text){}
    };
    AddressSuggestions(EditText address,View anchor,BrowserStore store,Consumer<String> navigate){
        this.address=address;this.anchor=anchor;this.store=store;this.navigate=navigate;
        popup=new ListPopupWindow(address.getContext());popup.setAnchorView(anchor);popup.setModal(false);
        popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NEEDED);popup.setBackgroundDrawable(new ColorDrawable(address.getContext().getColor(R.color.browser_surface)));
        popup.setAdapter(adapter);popup.setOnItemClickListener((parent,view,position,id)->choose(position));
        address.addTextChangedListener(changes);address.setOnFocusChangeListener((view,focused)->{if(focused)refresh();else dismiss();});
    }
    private int dp(int value){return Math.round(value*address.getResources().getDisplayMetrics().density);}
    private void refresh(){
        if(destroyed||!address.hasFocus()||!address.hasWindowFocus()||!address.isShown()||address.getText().toString().trim().isEmpty()){dismiss();return;}
        // At most 500 stored visits: synchronous filtering cannot publish an older query later.
        rows=HistorySuggestions.match(store.read("history"),address.getText().toString());selection=-1;adapter.notifyDataSetChanged();
        if(rows.isEmpty()||anchor.getWidth()==0){dismiss();return;}
        popup.setWidth(anchor.getWidth());popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);popup.show();popup.clearListSelection();
    }
    void dismiss(){popup.dismiss();selection=-1;rows=new ArrayList<>();adapter.notifyDataSetChanged();}
    boolean dismissIfShowing(){boolean showing=popup.isShowing();dismiss();return showing;}
    boolean arrow(int key,KeyEvent event){
        if(!popup.isShowing()||(key!=KeyEvent.KEYCODE_DPAD_DOWN&&key!=KeyEvent.KEYCODE_DPAD_UP))return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN){selection=Math.max(-1,Math.min(rows.size()-1,selection+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1)));popup.setSelection(selection);}
        return true;
    }
    boolean completeSelection(){if(!popup.isShowing()||selection<0)return false;choose(selection);return true;}
    private void choose(int position){
        if(position<0||position>=rows.size())return;String url=rows.get(position).url;
        // Recheck the current store so clearing/removing history cannot leave a usable stale row.
        boolean exists=false;for(BrowserStore.Entry entry:store.read("history"))if(entry.url.equals(url)){exists=true;break;}
        dismiss();if(exists)navigate.accept(url);
    }
    void appearanceChanged(){dismiss();popup.setBackgroundDrawable(new ColorDrawable(address.getContext().getColor(R.color.browser_surface)));}
    void destroy(){destroyed=true;dismiss();address.removeTextChangedListener(changes);address.setOnFocusChangeListener(null);}
    private final class Rows extends BaseAdapter {
        public int getCount(){return rows.size();}
        public BrowserStore.Entry getItem(int position){return rows.get(position);}
        public long getItemId(int position){return position;}
        public View getView(int position,View recycled,ViewGroup parent){
            LinearLayout view;
            if(recycled instanceof LinearLayout)view=(LinearLayout)recycled;
            else{
                view=new LinearLayout(address.getContext());view.setOrientation(LinearLayout.VERTICAL);view.setPadding(dp(14),dp(10),dp(14),dp(10));view.setMinimumHeight(dp(60));
                TextView title=new TextView(address.getContext());title.setTextSize(16);title.setTextColor(address.getContext().getColor(R.color.browser_ink));title.setTypeface(null,Typeface.BOLD);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);view.addView(title,new LinearLayout.LayoutParams(-1,-2));
                TextView url=new TextView(address.getContext());url.setTextSize(13);url.setTextColor(address.getContext().getColor(R.color.browser_hint));url.setSingleLine(true);url.setEllipsize(TextUtils.TruncateAt.END);view.addView(url,new LinearLayout.LayoutParams(-1,-2));
            }
            BrowserStore.Entry entry=getItem(position);((TextView)view.getChildAt(0)).setText(entry.title);((TextView)view.getChildAt(1)).setText(entry.url);return view;
        }
    }
}
