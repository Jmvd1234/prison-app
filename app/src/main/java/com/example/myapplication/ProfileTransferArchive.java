package com.example.myapplication;

import android.content.Context;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Versioned ZIP container. Never extract paths supplied by an archive. */
final class ProfileTransferArchive {
    static final long MAX_BYTES=256L*1024*1024;
    static final int MAX_PROFILES=10000;
    static final long MAX_RECORD_BYTES=2L*1024*1024;
    static final String MANIFEST="Laya profiles\n1\n";
    static final class Preview implements Closeable {
        final File file;
        final List<Profile> profiles;
        Preview(File f,List<Profile> p){file=f;profiles=p;}
        public void close(){file.delete();}
    }
    static void exportProfiles(Context context, List<Integer> ids,long owner,OutputStream destination)throws IOException {
        if(ids.isEmpty())throw new IOException("Select at least one profile.");
        if(ids.size()>MAX_PROFILES)throw new IOException("Export up to 10,000 profiles per file. You can export another batch afterwards.");
        AppDatabase db=AppDatabase.getInstance(context);
        List<Profile> profiles=db.runInTransaction(()->{
            List<Profile> selected=new ArrayList<>();
            for(int id:new LinkedHashSet<>(ids)){
                Profile p=db.ProfileDao().getProfileByProfileID(id);
                if(p==null||p.userID!=owner)throw new IllegalStateException("A selected profile is no longer available.");
                selected.add(p);
            }return selected;
        });
        try(ZipOutputStream zip=new ZipOutputStream(destination)){
            zip.putNextEntry(new ZipEntry("manifest.txt"));
            zip.write((MANIFEST+profiles.size()+"\n").getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            long total=0;
            for(int i=0;i<profiles.size();i++){
                Profile p=profiles.get(i);ByteArrayOutputStream json=new ByteArrayOutputStream();ProfileTransferCodec.write(p,json);
                if(json.size()>MAX_RECORD_BYTES)throw new IOException("A profile is too large to export.");
                total+=json.size();if(total>MAX_BYTES)throw new IOException("Backup exceeds 256 MB. Export fewer profiles at a time.");
                zip.putNextEntry(new ZipEntry("profiles/"+i+".json"));json.writeTo(zip);zip.closeEntry();
                if(p.image_path!=null&&!p.image_path.isEmpty()){
                    File photo=ProfilePhotoStore.resolve(context,p.image_path);
                    if(photo==null||!photo.isFile())throw new IOException("A selected profile photo is missing. Remove or replace it before exporting.");
                    total+=photo.length();if(photo.length()>32L*1024*1024||total>MAX_BYTES)throw new IOException("Backup exceeds 256 MB. Export fewer profiles at a time.");
                    zip.putNextEntry(new ZipEntry("photos/"+i+".jpg"));
                    try(InputStream in=new FileInputStream(photo)){copy(in,zip,32L*1024*1024);}zip.closeEntry();
                }
            }
        }
    }
    static Preview read(Context context,InputStream input)throws IOException {
        File file=File.createTempFile("laya-import-",".zip",context.getCacheDir());boolean success=false;
        try {
            try(OutputStream out=new FileOutputStream(file)){copy(input,out,MAX_BYTES);}
            List<Profile> profiles=new ArrayList<>();
            try(ZipFile zip=new ZipFile(file)){
                Set<String> names=new HashSet<>();long expanded=0;int count=0;
                Enumeration<? extends ZipEntry> entries=zip.entries();
                while(entries.hasMoreElements()){
                    ZipEntry e=entries.nextElement();String name=e.getName();
                    if(!names.add(name)||e.isDirectory()||!(name.equals("manifest.txt")||name.matches("profiles/[0-9]+\\.json")||name.matches("photos/[0-9]+\\.jpg")))
                        throw new IOException("Not a valid Laya backup.");
                    if(names.size()>1+2*MAX_PROFILES)throw new IOException("Too many files in this backup.");
                    long limit=name.equals("manifest.txt")?128:name.startsWith("profiles/")?MAX_RECORD_BYTES:32L*1024*1024;
                    try(CheckedInputStream in=new CheckedInputStream(zip.getInputStream(e),new CRC32())){
                        expanded+=copy(in,null,limit);
                        if(e.getCrc()!=in.getChecksum().getValue())throw new IOException("Damaged backup. Please export it again.");
                    }
                    if(expanded>MAX_BYTES)throw new IOException("Backup exceeds the 256 MB import limit.");
                    if(name.startsWith("profiles/"))count++;
                }
                ZipEntry manifest=zip.getEntry("manifest.txt");if(manifest==null)throw new IOException("Missing Laya backup header.");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=zip.getInputStream(manifest)){copy(in,bytes,128);}
                if(!bytes.toString("UTF-8").equals(MANIFEST+count+"\n"))throw new IOException("Unsupported or damaged Laya backup. Check that both devices use an up-to-date Laya version.");
                if(count>MAX_PROFILES)throw new IOException("Import up to 10,000 profiles per file.");
                if(count==0)throw new IOException("This backup contains no profiles.");
                for(int i=0;i<count;i++){
                    ZipEntry entry=zip.getEntry("profiles/"+i+".json");if(entry==null)throw new IOException("Missing profile in backup.");
                    try(InputStream in=zip.getInputStream(entry)){profiles.add(ProfileTransferCodec.read(in));}
                    catch(IOException|RuntimeException e){throw new IOException("Could not read profile "+(i+1)+". "+(e.getMessage()==null?"Invalid data.":e.getMessage()),e);}
                }
                for(String name:names)if(name.startsWith("photos/")){
                    String number=name.substring(7,name.length()-4);
                    if(!names.contains("profiles/"+number+".json"))throw new IOException("Photo has no matching profile.");
                }
            }
            success=true;return new Preview(file,profiles);
        }finally{if(!success)file.delete();}
    }
    static void restore(Context context,Preview preview,long owner)throws IOException {
        List<String> createdPhotos=new ArrayList<>();boolean success=false;
        try(ZipFile zip=new ZipFile(preview.file)){
            for(int i=0;i<preview.profiles.size();i++){
                Profile p=preview.profiles.get(i);p.image_path=null;
                ZipEntry entry=zip.getEntry("photos/"+i+".jpg");if(entry==null)continue;
                File temp=File.createTempFile("laya-photo-",".jpg",context.getCacheDir());
                try {
                    try(InputStream in=zip.getInputStream(entry);OutputStream out=new FileOutputStream(temp)){copy(in,out,32L*1024*1024);}
                    p.image_path=ProfilePhotoStore.restoreBackupPhoto(context,temp);createdPhotos.add(p.image_path);
                }finally{temp.delete();}
            }
            AppDatabase.getInstance(context).ProfileDao().importProfiles(preview.profiles,owner);
            success=true;
        }finally{
            if(!success)for(String path:createdPhotos){File f=ProfilePhotoStore.resolve(context,path);if(f!=null)f.delete();}
        }
    }
    static long copy(InputStream input,OutputStream output,long limit)throws IOException {
        byte[] buffer=new byte[16384];long total=0;int n;
        while((n=input.read(buffer))!=-1){total+=n;if(total>limit)throw new IOException("Backup or file is too large.");if(output!=null)output.write(buffer,0,n);}
        return total;
    }
}
