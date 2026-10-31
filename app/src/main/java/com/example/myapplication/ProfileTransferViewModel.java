package com.example.myapplication;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import java.io.*;
import java.util.*;

/** Retains selection and transfer work across rotation without retaining an Activity. */
public final class ProfileTransferViewModel extends AndroidViewModel {
    final MutableLiveData<Boolean> busy=new MutableLiveData<>(false);
    final MutableLiveData<String> message=new MutableLiveData<>();
    final MutableLiveData<Integer> previewCount=new MutableLiveData<>();
    final MutableLiveData<Boolean> imported=new MutableLiveData<>();
    private final SavedStateHandle state;
    private final Handler main=new Handler(Looper.getMainLooper());
    private ProfileTransferArchive.Preview preview;
    private boolean cleared;
    public ProfileTransferViewModel(Application app,SavedStateHandle state){super(app);this.state=state;}
    boolean isBusy(){return Boolean.TRUE.equals(busy.getValue());}
    boolean selecting(){return Boolean.TRUE.equals(state.get("selecting"));}
    void setSelecting(boolean value){state.set("selecting",value);if(!value)state.set("selected",new ArrayList<Integer>());}
    ArrayList<Integer> selected(){ArrayList<Integer> ids=state.get("selected");return ids==null?new ArrayList<>():new ArrayList<>(ids);}
    void select(int id,boolean checked){ArrayList<Integer> ids=selected();ids.remove(Integer.valueOf(id));if(checked)ids.add(id);state.set("selected",ids);}
    void selectAll(List<Integer> ids){state.set("selected",new ArrayList<>(ids));}
    void beginExport(long owner){state.set("pendingOwner",owner);state.set("pendingExport",selected());}
    void beginImport(long owner){state.set("pendingOwner",owner);}
    private long owner(){Long value=state.get("pendingOwner");return value==null?-1:value;}
    private void checkOwner(long owner)throws IOException{
        if(owner<0||getApplication().getSharedPreferences("user_session",0).getLong("currentUserID",-1)!=owner)
            throw new IOException("The signed-in profile changed. Please try again.");
    }
    void exportTo(Uri uri){
        if(uri==null||isBusy())return;
        final long owner=owner();final ArrayList<Integer> ids=state.get("pendingExport");busy.setValue(true);
        ProfilePhotoStore.IO.execute(()->{
            File temp=null;
            try{
                checkOwner(owner);if(ids==null)throw new IOException("Select profiles again.");
                temp=File.createTempFile("laya-export-",".laya",getApplication().getCacheDir());
                try(OutputStream out=new FileOutputStream(temp)){ProfileTransferArchive.exportProfiles(getApplication(),ids,owner,out);}
                checkOwner(owner);
                try(OutputStream out=getApplication().getContentResolver().openOutputStream(uri,"wt");InputStream in=new FileInputStream(temp)){
                    if(out==null)throw new IOException("Cannot write to that location.");ProfileTransferArchive.copy(in,out,ProfileTransferArchive.MAX_BYTES+1024*1024);
                }
                finish("Exported "+ids.size()+" profile"+(ids.size()==1?"":"s")+".",true);
            }catch(Exception e){finish("Export failed. "+detail(e),false);}
            finally{if(temp!=null)temp.delete();}
        });
    }
    void readFrom(Uri uri){
        if(uri==null||isBusy())return;discardPreview();busy.setValue(true);final long owner=owner();
        ProfilePhotoStore.IO.execute(()->{
            try{
                checkOwner(owner);ProfileTransferArchive.Preview prepared;
                try(InputStream in=getApplication().getContentResolver().openInputStream(uri)){
                    if(in==null)throw new IOException("Cannot open that file.");prepared=ProfileTransferArchive.read(getApplication(),in);
                }
                main.post(()->{busy.setValue(false);if(cleared){prepared.close();return;}preview=prepared;previewCount.setValue(prepared.profiles.size());});
            }catch(Exception e){finish("Import failed. "+detail(e),false);}
        });
    }
    void confirmImport(){
        if(preview==null||isBusy())return;busy.setValue(true);
        final ProfileTransferArchive.Preview prepared=preview;preview=null;previewCount.setValue(null);final long owner=owner();
        ProfilePhotoStore.IO.execute(()->{
            try{checkOwner(owner);ProfileTransferArchive.restore(getApplication(),prepared,owner);
                main.post(()->{busy.setValue(false);if(!cleared){imported.setValue(true);message.setValue("Imported "+prepared.profiles.size()+" profile"+(prepared.profiles.size()==1?"":"s")+".");}});
            }catch(Exception e){finish("Import failed. No profiles were added. "+detail(e),false);}
            finally{prepared.close();}
        });
    }
    void discardPreview(){if(preview!=null){preview.close();preview=null;}previewCount.setValue(null);}
    private static String detail(Exception e){return e.getMessage()==null?"Please try again.":e.getMessage();}
    private void finish(String text,boolean exported){main.post(()->{busy.setValue(false);if(cleared)return;if(exported)setSelecting(false);message.setValue(text);});}
    @Override protected void onCleared(){cleared=true;if(preview!=null)preview.close();}
}
