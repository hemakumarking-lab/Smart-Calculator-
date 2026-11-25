package com.smartcalc.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private final int BLUE = Color.rgb(31,126,190);
    private final int ORANGE = Color.rgb(255,194,77);
    private TextView display, cgst, sgst, igst, tax, total, expression;
    private LinearLayout root, quickRows;
    private String current="0", operator=null;
    private Double first=null;
    private boolean fresh=false;
    private double[] plusRates={3,5,18,40}, minusRates={3,5,18,40};
    private int precision=2;
    private boolean darkTheme=false;
    private SharedPreferences prefs;
    private DecimalFormat fmt;
    private final ArrayList<String> history=new ArrayList<>();
    private double lastBase=0, lastRate=0;
    private boolean lastAdd=true;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs=getSharedPreferences("smartcalc_preferences",MODE_PRIVATE);
        loadPrefs(); buildUi();
    }

    private void loadPrefs() {
        double[] defaults={3,5,18,40};
        for(int i=0;i<4;i++){
            plusRates[i]=Double.longBitsToDouble(prefs.getLong("plus_"+i,Double.doubleToLongBits(defaults[i])));
            minusRates[i]=Double.longBitsToDouble(prefs.getLong("minus_"+i,Double.doubleToLongBits(defaults[i])));
        }
        precision=prefs.getInt("precision",2); darkTheme=prefs.getBoolean("dark_theme",false); updateFormat();
    }
    private void updateFormat(){
        String p="#,##0";
        if(precision>0)p+="."+new String(new char[precision]).replace("\0","0");
        fmt=new DecimalFormat(p,DecimalFormatSymbols.getInstance(Locale.US));
    }
    private String f(double v){return fmt.format(v);}
    private void buildUi(){
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(darkTheme?Color.rgb(16,24,40):Color.WHITE);setContentView(root);

        LinearLayout resultArea=new LinearLayout(this);resultArea.setOrientation(LinearLayout.VERTICAL);resultArea.setPadding(18,8,18,6);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView appTitle=new TextView(this);appTitle.setText("SmartCalc");appTitle.setTextColor(darkTheme?Color.WHITE:Color.DKGRAY);appTitle.setTextSize(18);appTitle.setTypeface(null,Typeface.BOLD);
        header.addView(appTitle,new LinearLayout.LayoutParams(0,38,1));
        TextView settingsBtn=new TextView(this);settingsBtn.setText("⚙");settingsBtn.setTextSize(24);settingsBtn.setTextColor(darkTheme?Color.WHITE:Color.GRAY);settingsBtn.setGravity(Gravity.CENTER);
        header.addView(settingsBtn,new LinearLayout.LayoutParams(48,38));settingsBtn.setOnClickListener(v->showSettings());
        resultArea.addView(header);
        root.addView(resultArea,new LinearLayout.LayoutParams(-1,0,4.1f));
        expression=new TextView(this);expression.setTextColor(darkTheme?Color.LTGRAY:Color.GRAY);expression.setTextSize(14);expression.setGravity(Gravity.RIGHT);resultArea.addView(expression,new LinearLayout.LayoutParams(-1,24));
        display=new TextView(this);display.setText(f(parseCurrent()));display.setTextColor(darkTheme?Color.WHITE:Color.DKGRAY);display.setTextSize(38);display.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);display.setSingleLine(true);
        resultArea.addView(display,new LinearLayout.LayoutParams(-1,0,1));
        cgst=detail(resultArea,"CGST [9.0%]");sgst=detail(resultArea,"SGST [9.0%]");igst=detail(resultArea,"IGST [18.0%]");
        tax=detail(resultArea,"Tax [18.0%]");tax.setTextSize(25);total=detail(resultArea,"Total");total.setTextSize(27);

        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);actions.setPadding(5,3,5,3);actions.setBackgroundColor(Color.WHITE);
        root.addView(actions,new LinearLayout.LayoutParams(-1,45));
        addAction(actions,"●","Share",()->shareResult());
        addAction(actions,"▢","Copy",()->copyResult());
        addAction(actions,"ⓦ","Currency",()->new AlertDialog.Builder(this).setMessage("Currency conversion is not configured.").setPositiveButton("OK",null).show());
        addAction(actions,"◴","History",()->showHistory());

        quickRows=new LinearLayout(this);quickRows.setOrientation(LinearLayout.VERTICAL);quickRows.setBackgroundColor(darkTheme?Color.rgb(25,35,50):Color.rgb(246,246,246));quickRows.setPadding(4,2,4,2);
        root.addView(quickRows,new LinearLayout.LayoutParams(-1,0,1.45f));rebuildRates();

        String[][] keys={{"7","8","9","÷","="},{"4","5","6","×","⌫"},{"1","2","3","−","AC"},{"0","00",".","+","%"}};
        LinearLayout keypad=new LinearLayout(this);keypad.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(keypad,new LinearLayout.LayoutParams(-1,0,3.8f));
        LinearLayout digits=new LinearLayout(this);digits.setOrientation(LinearLayout.VERTICAL);keypad.addView(digits,new LinearLayout.LayoutParams(0,-1,4));
        LinearLayout side=new LinearLayout(this);side.setOrientation(LinearLayout.VERTICAL);keypad.addView(side,new LinearLayout.LayoutParams(0,-1,1));
        for(int r=0;r<4;r++){
            LinearLayout row=new LinearLayout(this);digits.addView(row,new LinearLayout.LayoutParams(-1,0,1));
            for(int c=0;c<4;c++){String k=keys[r][c];Button b=key(k);row.addView(b,new LinearLayout.LayoutParams(0,-1,1));b.setOnClickListener(v->press(k));}
            String sk=keys[r][4];Button s=key(sk);s.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ORANGE));s.setTextColor(Color.WHITE);
            side.addView(s,new LinearLayout.LayoutParams(-1,0,1));s.setOnClickListener(v->press(sk));
        }
        refreshBreakdown(0,0,true);
    }
    private TextView detail(LinearLayout parent,String label){
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);parent.addView(line,new LinearLayout.LayoutParams(-1,0,1));
        TextView l=new TextView(this);l.setText(label);l.setTextColor(darkTheme?Color.LTGRAY:Color.DKGRAY);l.setTextSize(19);line.addView(l,new LinearLayout.LayoutParams(0,-2,1));
        TextView v=new TextView(this);v.setText("0.00");v.setTextColor(darkTheme?Color.WHITE:Color.DKGRAY);v.setTextSize(19);v.setGravity(Gravity.RIGHT);line.addView(v);return v;
    }
    private Button key(String text){
        Button b=new Button(this);b.setText(text);b.setTextSize(23);b.setAllCaps(false);b.setTextColor(BLUE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(darkTheme?Color.rgb(35,47,66):Color.WHITE));
        b.setPadding(0,0,0,0);b.setStateListAnimator(null);
        b.setBackground(new android.graphics.drawable.GradientDrawable(){ {setColor(darkTheme?Color.rgb(35,47,66):Color.WHITE);setStroke(1,Color.LTGRAY);} });
        return b;
    }
    private void addAction(LinearLayout row,String glyph,String label,Runnable action){
        TextView t=new TextView(this);t.setText(glyph);t.setTextSize(27);t.setTextColor(Color.GRAY);t.setGravity(Gravity.CENTER);
        row.addView(t,new LinearLayout.LayoutParams(0,-1,1));t.setOnClickListener(v->action.run());
    }
    private void rebuildRates(){
        quickRows.removeAllViews();addRateRow(plusRates,true);addRateRow(minusRates,false);
    }
    private void addRateRow(double[] rates,boolean add){
        LinearLayout row=new LinearLayout(this);quickRows.addView(row,new LinearLayout.LayoutParams(-1,0,1));
        for(double rate:rates){
            TextView t=new TextView(this);t.setText((add?"+":"−")+rate+"%");t.setTextColor(darkTheme?Color.rgb(90,175,235):BLUE);t.setTextSize(19);t.setGravity(Gravity.CENTER);
            row.addView(t,new LinearLayout.LayoutParams(0,-1,1));t.setOnClickListener(v->applyTax(rate,add));
        }
    }
    private void applyTax(double rate,boolean add){
        double base=parseCurrent();lastBase=base;lastRate=rate;lastAdd=add;
        double amount=base*rate/100.0;double result=add?base+amount:base-amount;
        expression.setText("");current=fmt.format(result).replace(",","");fresh=true;display.setText(f(result));
        refreshBreakdown(base,rate,add);
        history.add(f(base)+(add?" + ":" − ")+rate+"% = "+f(result));
    }
    private void refreshBreakdown(double base,double rate,boolean add){
        double half=base*rate/200.0,whole=base*rate/100.0,sign=add?1:-1;
        cgst.setText(f(half));sgst.setText(f(half));igst.setText(f(whole));tax.setText((add?"+":"−")+f(whole));
        total.setText(f(base+sign*whole));
    }
    private void press(String k){
        try{
            if(k.matches("[0-9]+")||k.equals(".")){
                if(fresh){current=k.equals(".")?"0.":k;fresh=false;}
                else if(k.equals(".")&&current.contains("."))return;
                else current=current.equals("0")&&!k.equals(".")?k:current+k;
                display.setText(f(parseCurrent()));refreshBreakdown(0,0,true);clearTaxLabels();
            }else if(k.equals("AC")){current="0";first=null;operator=null;fresh=false;expression.setText("");display.setText("0");clearTaxLabels();}
            else if(k.equals("⌫")){if(!fresh)current=current.length()>1?current.substring(0,current.length()-1):"0";display.setText(f(parseCurrent()));}
            else if(k.equals("%")){current=fmt.format(parseCurrent()/100).replace(",","");display.setText(f(parseCurrent()));}
            else if(k.equals("="))calculate();
            else{first=parseCurrent();operator=k;expression.setText(f(first)+" "+k);fresh=true;}
        }catch(Exception e){current="0";display.setText("Error");}
    }
    private void clearTaxLabels(){cgst.setText("0.00");sgst.setText("0.00");igst.setText("0.00");tax.setText("+0.00");total.setText(f(parseCurrent()));}
    private double parseCurrent(){try{return Double.parseDouble(current);}catch(Exception e){return 0;}}
    private void calculate(){
        if(first==null||operator==null)return;double a=first,b=parseCurrent(),r;
        if(operator.equals("+")||operator.equals("−")||operator.equals("×"))b=a*b/100.0;else if(operator.equals("÷"))b=b/100.0;
        switch(operator){case "+":r=a+b;break;case "−":r=a-b;break;case "×":r=b;break;case "÷":r=b==0?Double.NaN:a/b;break;default:return;}
        current=Double.isFinite(r)?fmt.format(r).replace(",",""):"0";display.setText(Double.isFinite(r)?f(r):"Error");
        expression.setText(f(a)+" "+operator+" "+f(b));history.add(expression.getText()+" = "+display.getText());first=null;operator=null;fresh=true;clearTaxLabels();
    }
    private void shareResult(){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,"SmartCalc result: "+display.getText());startActivity(Intent.createChooser(i,"Share result"));}
    private void copyResult(){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("SmartCalc",display.getText()));Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();}
    private void showHistory(){String[] items=history.toArray(new String[0]);new AlertDialog.Builder(this).setTitle("Calculation history").setItems(items,(d,w)->{String s=items[w];Toast.makeText(this,s,Toast.LENGTH_LONG).show();}).setPositiveButton("Close",null).show();}
    private void showSettings(){
        LinearLayout box=new LinearLayout(this);box.setPadding(18,5,18,0);box.setOrientation(LinearLayout.VERTICAL);
        EditText p=new EditText(this);p.setSingleLine(true);p.setHint("+ rates: 3,5,18,40");p.setText(join(plusRates));box.addView(p);
        EditText n=new EditText(this);n.setSingleLine(true);n.setHint("- rates: 3,5,18,40");n.setText(join(minusRates));box.addView(n);
        CheckBox dark=new CheckBox(this);dark.setText("Dark theme");dark.setChecked(darkTheme);box.addView(dark);\n        Spinner sp=new Spinner(this);String[] opts={"0","1","2","3","4","5","6"};sp.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,opts));sp.setSelection(precision);box.addView(sp);
        new AlertDialog.Builder(this).setTitle("SmartCalc settings").setView(box).setPositiveButton("Save",(d,w)->{
            double[] a=parseRates(p.getText().toString()),b=parseRates(n.getText().toString());
            if(a==null||b==null){Toast.makeText(this,"Enter four valid rates (0–1000), separated by commas.",Toast.LENGTH_LONG).show();return;}
            plusRates=a;minusRates=b;precision=sp.getSelectedItemPosition();darkTheme=dark.isChecked();SharedPreferences.Editor e=prefs.edit().putInt("precision",precision).putBoolean("dark_theme",darkTheme);
            for(int j=0;j<4;j++){e.putLong("plus_"+j,Double.doubleToLongBits(a[j]));e.putLong("minus_"+j,Double.doubleToLongBits(b[j]));}e.apply();updateFormat();buildUi();
        }).setNeutralButton("Restore defaults",(d,w)->{prefs.edit().clear().apply();current="0";loadPrefs();buildUi();Toast.makeText(this,"Defaults restored",Toast.LENGTH_SHORT).show();}).setNegativeButton("Cancel",null).show();
    }
    private String join(double[] a){return rateText(a[0])+","+rateText(a[1])+","+rateText(a[2])+","+rateText(a[3]);}
    private String rateText(double d){return d==(int)d?String.valueOf((int)d):String.valueOf(d);}
    private double[] parseRates(String s){try{String[] x=s.split(",");if(x.length!=4)return null;double[] a=new double[4];for(int i=0;i<4;i++){a[i]=Double.parseDouble(x[i].trim());if(!Double.isFinite(a[i])||a[i]<0||a[i]>1000)return null;}return a;}catch(Exception e){return null;}}
}
