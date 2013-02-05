package com.zlab.datFM;

import android.os.AsyncTask;
import android.view.View;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;

public class datFM_Properties_Operation extends AsyncTask<ArrayList<String>, Void, String> {

    Long holder;
    ArrayList<String> paths;

    protected void onPreExecute() {
        datFM_Properties.prop_size_progress.setVisibility(View.VISIBLE);
        super.onPreExecute();
    }
    /*
    protected void onProgressUpdate(Long values) {
        //datFM_Properties.datFM_Properties_state.recall_size(values);
    }        */

    @Override
    protected String doInBackground(ArrayList<String>... list) {
        paths = list[0];
        for (int i=0;i<paths.size();i++){

            File f = new File(paths.get(i));
            if(f.isDirectory()){
                get_directory(f);
            } else {
                get_file(f);
            }
        }
        return null;
    }

    protected void onPostExecute(String result) {
        datFM_Properties.prop_size_progress.setVisibility(View.GONE);
        if(holder!=null){
        BigDecimal size_d = new BigDecimal(holder/1024.00/1024.00);
        BigDecimal file_size_d = size_d.setScale(3, BigDecimal.ROUND_HALF_UP);
        datFM_Properties.prop_size.setText(String.valueOf(file_size_d));
        } else {
        datFM_Properties.prop_size.setText("0.00");
        }
        super.onPostExecute(result);
    }

    void get_directory(File file){
        try{
        for(File f : file.listFiles()){
            if (f.isDirectory()){
                get_directory(f);
            } else {
                get_file(f);
            }
        }
        } catch (Exception e){}
    }
    void get_file(File f){
        if(holder==null){
            holder=f.length();
        } else {
            holder=holder+f.length();
        }
        //onProgressUpdate(holder);
    }

}
