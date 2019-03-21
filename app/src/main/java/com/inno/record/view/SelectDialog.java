package com.inno.record.view;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.inno.record.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * Created by liuyin on 2018/7/17
 */
public class SelectDialog extends Dialog {



    public SelectDialog(@NonNull Context context) {
        super(context, R.style.ThemeCustomDialog);
        super.setContentView(R.layout.dialog_repairs_select_type);
        ListView listView= (ListView) findViewById(R.id.lv_bluetooth);
        Set<BluetoothDevice> pairedDevices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
        final List<BluetoothDevice> list=new ArrayList<>(pairedDevices);
        MyAdapter adapter =new MyAdapter(list);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                BluetoothDevice device=list.get(i);
                listener.callBack(device);
                dismiss();
            }
        });
    }

    public interface BlueToothSelectInterface{
        void callBack(BluetoothDevice device);
    }
    BlueToothSelectInterface listener;
    public void setSelectListener(BlueToothSelectInterface listener){
        this.listener=listener;
    }


    class MyAdapter extends BaseAdapter{

        List<BluetoothDevice> list;

        MyAdapter(List<BluetoothDevice> list){
            this.list=list;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int i) {
            return list.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            view= LayoutInflater.from(getContext()).inflate(R.layout.item_bluetooth,null);
            TextView textView= (TextView) view.findViewById(R.id.tv_item_bluetooth_title);
            textView.setText(list.get(i).getName());
            return view;
        }
    }


}
