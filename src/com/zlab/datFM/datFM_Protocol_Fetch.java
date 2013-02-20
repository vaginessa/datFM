package com.zlab.datFM;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import jcifs.UniAddress;
import jcifs.smb.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class datFM_Protocol_Fetch extends AsyncTask<String, Void, List<datFM_FileInformation>> {

    List<datFM_FileInformation> dir_info,fls_info;
    ProgressDialog dialog_operation_remote;
    String path,protocol;
    int panel_ID;
    String user, pass, url, hostname, domain;
    boolean fetch_err=false;
    boolean success_auth=true;
    boolean valid_url=true;
    NtlmPasswordAuthentication auth;

    public datFM activity;
    public datFM_Protocol_Fetch(datFM a){activity = a;}

    protected void onPreExecute() {
        if(!datFM.protocols[datFM.curPanel].equals("local") && !datFM.protocols[datFM.curPanel].equals("datfm")){
            dialog_operation_remote = new ProgressDialog(activity);
            dialog_operation_remote.setTitle(datFM.protocols[datFM.curPanel]);
            dialog_operation_remote.setMessage("Please wait...");
            dialog_operation_remote.setIndeterminate(false);
            dialog_operation_remote.show();}
        super.onPreExecute();
    }
    protected void onProgressUpdate(Integer values) {
        // dialog_operation.setMax(values[1]);
    }
    @Override
    protected List<datFM_FileInformation> doInBackground(String... paths) {
        path = paths[0];
        protocol = paths[1];
        //datFM.protocol=protocol;
        panel_ID = Integer.parseInt(paths[2]);
        //datFM.id= String.valueOf(panel_ID);

        dir_info = new ArrayList<datFM_FileInformation>();
        fls_info = new ArrayList<datFM_FileInformation>();

        datFM.curPanel = panel_ID;
        if(panel_ID==0){
            datFM.competPanel=1;
        }else{
            datFM.competPanel=0;
        }

        if (protocol.equals("local")){
            fetch_local();
        } else if (protocol.equals("smb")){
            fetch_smb();
        } else if (protocol.equals("datfm")){
            fetch_datFM();
        } else {
            Log.e("UNSUPPORT PROTOCOL:", protocol);
        }

        return dir_info;
    }
    protected void onPostExecute(List<datFM_FileInformation> result) {
        super.onPostExecute(result);
        if(!datFM.protocols[panel_ID].equals("local") && dialog_operation_remote !=null){
            dialog_operation_remote.dismiss();
        }
        //if(fetch_err){datFM.notify_toast("Listing error!");}

            if(protocol.equals("smb") && (!success_auth || fetch_err)){
                if(!success_auth){datFM.notify_toast(datFM.datFM_state.getResources().getString(R.string.notify_logon_error));}
                if(fetch_err){datFM.notify_toast(datFM.datFM_state.getResources().getString(R.string.notify_connection_error));}
                if(datFM.pref_sambalogin){
                    logonScreenSMB();}
            } else {
                datFM.datFM_state.fill_panel(dir_info, fls_info, panel_ID);
            }

        //Toast.makeText(datFM.datf_context,result.length,Toast.LENGTH_SHORT).show();
    }

    private void fetch_smb(){
        if(path.lastIndexOf("/")!=path.length()-1){path=path+"/";}
        url = path;datFM.url=url;
        user = datFM.user;
        pass = datFM.pass;
        domain = datFM.domain;
        //if(domain==null&&hostname.length()>0){domain=hostname;}
        auth = new NtlmPasswordAuthentication(domain, user, pass);

        // ------ CHECK SMB AUTH ------------ //
        if(datFM.pref_sambalogin){
            hostname = url;
            hostname = hostname.replace("smb://","");
            if(hostname.contains("/")){hostname = hostname.substring(0, hostname.indexOf("/"));}

            if(!hostname.equals("")){
                UniAddress uniaddress = null;
                try {
                        uniaddress = UniAddress.getByName(hostname);
                } catch (UnknownHostException e) {
                        valid_url=false;
                }
                if(valid_url){
                    try {
                        SmbSession.logon(uniaddress, auth);
                    } catch (SmbAuthException e ) {
                        // AUTHENTICATION FAILURE
                        success_auth=false;
                    } catch(SmbException e ) {
                        // NETWORK PROBLEMS?
                        fetch_err=true;
                    }/* catch (Exception e){*/
                    /*Log.e("ERR:",e.getMessage());*//*fetch_err=true;*/
                    /*}*/
                } else {
                    success_auth=false;
                }
            } else {
                valid_url=false;success_auth=false;
            }
        }
        //---------START SMB WORKS-------------------------
        //if(success_auth || url.equals("smb://")){
            try {
                SmbFile dir = null;
                dir = new SmbFile(url, auth);
                if (panel_ID ==0){
                    datFM.parent_left=dir.getParent();
                    datFM.curentLeftDir=dir.getPath();
                } else {
                    datFM.parent_right=dir.getParent();
                    datFM.curentRightDir=dir.getPath();}

                if(dir!=null){
                    try{
                        for(SmbFile ff: dir.listFiles()){
                            if(!datFM.pref_show_hide && ff.getName().endsWith("$/")){} else {
                                if(ff.isDirectory()){
                                    String data = datFM.datf_context.getResources().getString(R.string.fileslist_directory);
                                    dir_info.add(new datFM_FileInformation(ff.getName(),ff.getPath(),0,"smb","dir",data, ff.getParent()));
                                } else {
                                    BigDecimal size = new BigDecimal(ff.length()/1024.00/1024.00);
                                    size = size.setScale(2, BigDecimal.ROUND_HALF_UP);
                                    fls_info.add(new datFM_FileInformation(ff.getName(),ff.getPath(),ff.length(),"smb","file",size+" MiB",ff.getParent()));
                                }
                            }
                        }
                    } catch (Exception e) {
                        //fetch_err=true;
                    }
                }

                Collections.sort(dir_info);
                Collections.sort(fls_info);

                dir_info.addAll(fls_info);
                if(dir_info.size()>0){
                    success_auth=true;
                    fetch_err=false;
                }

            } catch (MalformedURLException e) {
                fetch_err=true;
            }
        //-------END SMB WORKS---------------------


            //if(dir_info.size()==0){fetch_err=true;}
            //} else {
            //logonScreenSMB();
        //}
    }
    protected void fetch_datFM(){
        String section=path.replace("datFM://", "");

        if (panel_ID ==0){
            datFM.parent_left=path;
            datFM.curentLeftDir=path;
        } else {
            datFM.parent_right=path;
            datFM.curentRightDir=path;}

        if (section.contains("/")){
            section=section.substring(0,section.indexOf("/")-1);
        } else if(section.equals("")){
            section="home";
        }

        if(section.equals("home")){
            dir_info.add(new datFM_FileInformation("External Storage", Environment.getExternalStorageDirectory().getPath(),0,"local","sdcard",
                    getAvailableExternalMemorySize()+" / "+getTotalExternalMemorySize(), "datFM://"));

            dir_info.add(new datFM_FileInformation("Root","/",0,"local","root",
                    getAvailableInternalMemorySize()+" / "+getTotalInternalMemorySize(), "datFM://"));

            dir_info.add(new datFM_FileInformation("Favorite","datFM://favorite",0,"local","favorite","Favorites", "datFM://"));

            dir_info.add(new datFM_FileInformation("Samba","smb://",0,"smb","network","Network", "datFM://"));
        } else if(section.equals("favorite")){
            dir_info.add(new datFM_FileInformation("Add to favorite","datFM://favorite/add",0,"smb","dir","Network", "datFM://"));
        }

        //dir_info.add(new datFM_FileInformation(ff.getName(),ff.getPath(),0,"smb","dir",data, ff.getParent()));
    }
    private void fetch_local(){
        File dir = new File (path);
        File[] dirs;

        if (datFM.pref_root){
            dirs = dir.listFiles();
            if(dirs==null){
                dirs = root_get_content(dir);}
        } else {
            dirs = dir.listFiles();
        }

        if (panel_ID ==0){
            datFM.parent_left=dir.getParent();
            datFM.curentLeftDir=dir.getPath();
        } else {
            datFM.parent_right=dir.getParent();
            datFM.curentRightDir=dir.getPath();}


        try{
            for(File ff: dirs)
            {
                if(!datFM.pref_show_hide && ff.getName().startsWith(".")){} else {
                    if(ff.isDirectory()){
                        String data = datFM.datf_context.getResources().getString(R.string.fileslist_directory);
                        dir_info.add(new datFM_FileInformation(ff.getName(),ff.getPath(),0,"smb","dir",data, ff.getParent()));
                    } else {
                        BigDecimal size = new BigDecimal(ff.length()/1024.00/1024.00);
                        size = size.setScale(2, BigDecimal.ROUND_HALF_UP);
                        fls_info.add(new datFM_FileInformation(ff.getName(),ff.getPath(),ff.length(),"smb","file",size+" MiB",ff.getParent()));
                    }
                }
            }
        }catch(Exception e){}
        Collections.sort(dir_info);
        Collections.sort(fls_info);

        dir_info.addAll(fls_info);
    }

    private void logonScreenSMB(){
        AlertDialog.Builder action_dialog = new AlertDialog.Builder(datFM.datFM_state);
        action_dialog.setTitle("Samba logon");
        LayoutInflater inflater = datFM.datFM_state.getLayoutInflater();
        View layer = inflater.inflate(R.layout.datfm_logon_smb,null);

        final EditText domains = (EditText) layer.findViewById(R.id.logon_domain);
        final EditText names   = (EditText) layer.findViewById(R.id.logon_name);
        final EditText passs   = (EditText) layer.findViewById(R.id.logon_pass);

        domains.setText(datFM.domain);
        names.setText(datFM.user);
        passs.setText(datFM.pass);

        final CheckBox logon_guest = (CheckBox) layer.findViewById(R.id.logon_guest);
        logon_guest.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    domains.setText("");domains.setEnabled(false);
                    names.setText("");names.setEnabled(false);
                    passs.setText("");passs.setEnabled(false);
                } else {
                    domains.setText(datFM.domain);domains.setEnabled(true);
                    names.setText(datFM.user);names.setEnabled(true);
                    passs.setText(datFM.pass);passs.setEnabled(true);
                }
            }
        });

        action_dialog.setView(layer);
        action_dialog.setPositiveButton(datFM.datFM_state.getResources().getString(R.string.ui_dialog_btn_ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String domain_n,user_n,pass_n;
                        domain_n = domains.getText().toString();
                        user_n = names.getText().toString();
                        pass_n = passs.getText().toString();
                        //datFM.curPanel = Integer.parseInt(datFM.id);

                        if(domain_n.equals("")){
                            datFM.domain = null;
                        } else {
                            datFM.domain = domain_n;
                        }

                        if(user_n.equals("")){
                            datFM.user = null;
                        } else {
                            datFM.user = user_n;
                        }

                        if(pass_n.equals("")){
                            datFM.pass = null;
                        } else {
                            datFM.pass = pass_n;
                        }

                        new datFM_Protocol_Fetch(datFM.datFM_state).execute(path, protocol, String.valueOf(panel_ID));
                        //fetch_smb();
                    }
                });
        action_dialog.setNegativeButton(datFM.datFM_state.getResources().getString(R.string.ui_dialog_btn_cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
        AlertDialog AboutDialog = action_dialog.create();
        AboutDialog.show();
    }

    private File[] root_get_content(File d){
        File[] dirs;
        String out = new String();
        String command = "ls \""+d.getPath()+"\"\n";

        Process p;
        try {
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", "/system/bin/sh"});
            DataOutputStream stdin = new DataOutputStream(p.getOutputStream());
            byte[] buf = command.getBytes("UTF-8");
            stdin.write(buf,0,buf.length);

            stdin.writeBytes("echo \n");
            DataInputStream stdout = new DataInputStream(p.getInputStream());
            byte[] buffer = new byte[4096];
            int read;
            while(true){
                read = stdout.read(buffer);
                out += new String(buffer, 0, read);
                if(read<4096){
                    break;
                }
                // here is where you catch the error value
                //int len = out.length();
                //char suExitValue = out.charAt(len-2);
                //Toast.makeText(getApplicationContext(), String.valueOf(suExitValue), Toast.LENGTH_SHORT).show();
                //return0or1(Integer.valueOf(suExitValue), command); // 0 or 1 Method
                // end catching exit value
            }
            stdin.writeBytes("exit\n");
            stdin.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String[] dirs_array = out.split("\n");
        dirs=new File[dirs_array.length];
        for (int i=0;i<dirs_array.length;i++){
            dirs[i]=new File (d,dirs_array[i]);
        }

        return dirs;
    }

    public static boolean externalMemoryAvailable() {
        return android.os.Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED);
    }
    public static String getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return formatSize(availableBlocks * blockSize);
    }
    public static String getTotalInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long totalBlocks = stat.getBlockCount();
        return formatSize(totalBlocks * blockSize);
    }
    public static String getAvailableExternalMemorySize() {
        if (externalMemoryAvailable()) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            return formatSize(availableBlocks * blockSize);
        } else {
            return "ERROR";
        }
    }
    public static String getTotalExternalMemorySize() {
        if (externalMemoryAvailable()) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long totalBlocks = stat.getBlockCount();
            return formatSize(totalBlocks * blockSize);
        } else {
            return "ERROR";
        }
    }
    public static String formatSize(long size) {
        String suffix = null;
        BigDecimal size_comma=new BigDecimal(0);

        if (size >= 1024) {
            suffix = "KB";
            size_comma=new BigDecimal(size/1024.00);
            size /= 1024;
            if (size >= 1024) {
                suffix = " MiB";
                size_comma=new BigDecimal(size/1024.00);
                size /= 1024;
                if (size >= 1024) {
                    suffix = " GiB";
                    size_comma=new BigDecimal(size/1024.00);
                    size /= 1024;
                }
            }
        }

        size_comma = size_comma.setScale(1, BigDecimal.ROUND_HALF_UP);
        StringBuilder resultBuffer = new StringBuilder(String.valueOf(size_comma));

        /*
        int commaOffset = resultBuffer.length() - 3;
        while (commaOffset > 0) {
            resultBuffer.insert(commaOffset, ',');
            commaOffset -= 3;
        }
        */

        if (suffix != null) resultBuffer.append(suffix);
        return resultBuffer.toString();
    }

}