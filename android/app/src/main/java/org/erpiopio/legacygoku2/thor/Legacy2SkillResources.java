package org.erpiopio.legacygoku2.thor;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

final class Legacy2SkillResources {
    private static final String SHA1="db36ff52fcd63f753f9d66439aa3d2216701c326";
    private static native void extractNative(String rom,String output)throws IOException;
    private static final HashMap<String,Bitmap> icons=new HashMap<>();
    private static volatile boolean ready;
    private static boolean started;
    private static File directory(Context c){
        return new File(c.getFilesDir(),"skills/"+SHA1+"/v1");
    }
    private static void validate(File dir)throws Exception{
        JSONObject m=new JSONObject(new String(Files.readAllBytes(
            new File(dir,"manifest.json").toPath()),StandardCharsets.UTF_8));
        if(m.getInt("extractorVersion")!=1||!SHA1.equals(m.getString("romSha1"))||
            !"ALFP".equals(m.getString("romCode"))||m.getInt("specialCount")!=10||
            m.getInt("physicalCount")!=22)throw new IOException("Invalid skill manifest");
        if(new File(dir,"menu.pal").length()!=512||new File(dir,"objects.pal").length()!=512)
            throw new IOException("Incomplete skill palettes");
        for(int id=0;id<22;++id)validateImage(new File(dir,"physical-"+id+".idx"));
        for(int id=0;id<5;++id)for(int s=1;s<=2;++s)
            validateImage(new File(dir,"special-"+id+"-"+s+".idx"));
    }
    private static byte[] validateImage(File file)throws IOException{
        byte[] data=Files.readAllBytes(file.toPath());
        if(data.length<4)throw new IOException("Missing skill image");
        int w=data[0]&255,h=data[1]&255,p=data[2]&255;
        if(w<1||h<1||w>64||h>64||p>1||data[3]!=0||data.length!=4+w*h)
            throw new IOException("Invalid skill image");
        return data;
    }
    static synchronized void prepare(Context c,File rom)throws Exception{
        File target=directory(c);
        try{validate(target);return;}catch(Exception missing){/* Generate below. */}
        if(rom.length()!=8388608)throw new IOException("Invalid ALFP ROM size");
        MessageDigest digest=MessageDigest.getInstance("SHA-1");
        try(FileInputStream in=new FileInputStream(rom)){
            byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);
        }
        StringBuilder hash=new StringBuilder();
        for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
        if(!SHA1.contentEquals(hash))throw new IOException("Skills require verified ALFP ROM");
        System.loadLibrary("trilogy_legacy2_resources");
        File parent=target.getParentFile();
        if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create skill cache");
        File stage=new File(parent,"staging-"+UUID.randomUUID());
        if(!stage.mkdir())throw new IOException("Cannot stage skill extraction");
        try{
            extractNative(rom.getAbsolutePath(),stage.getAbsolutePath());validate(stage);
            if(target.exists()){
                File old=new File(parent,"previous-"+System.currentTimeMillis());
                if(!target.renameTo(old))throw new IOException("Cannot preserve skill cache");
                if(!stage.renameTo(target)){
                    old.renameTo(target);throw new IOException("Cannot activate skill cache");
                }
            }else if(!stage.renameTo(target))throw new IOException("Cannot activate skill cache");
        }finally{removeStage(stage);}
    }
    private static void removeStage(File dir){
        File[] files=dir.listFiles();
        if(files!=null)for(File file:files)file.delete();
        dir.delete();
    }
    private static synchronized void start(Context context){
        if(started)return;started=true;
        Context app=context.getApplicationContext();
        new Thread(()->{
            try{
                prepare(app,new File(app.getFilesDir(),
                    "roms/legacy_of_goku_ii_europe_alfp.gba"));
                HashMap<String,Bitmap> loaded=new HashMap<>();
                File dir=directory(app);
                byte[][] palettes={Files.readAllBytes(new File(dir,"menu.pal").toPath()),
                    Files.readAllBytes(new File(dir,"objects.pal").toPath())};
                for(int id=0;id<22;++id)load(loaded,dir,"physical-"+id,palettes);
                for(int id=0;id<5;++id)for(int slot=1;slot<=2;++slot)
                    load(loaded,dir,"special-"+id+"-"+slot,palettes);
                synchronized(Legacy2SkillResources.class){icons.putAll(loaded);ready=true;}
            }catch(Exception error){Log.e("LEGACY2_SKILLS","Cannot prepare original skill sprites",error);}
        },"Legacy2-skill-sprites").start();
    }
    private static void load(HashMap<String,Bitmap> out,File dir,String name,byte[][] palettes)
            throws IOException{
        byte[] data=validateImage(new File(dir,name+".idx"));
        int w=data[0]&255,h=data[1]&255;byte[] pal=palettes[data[2]&255];
        int[] pixels=new int[w*h];
        for(int i=0;i<pixels.length;++i){
            int index=data[i+4]&255;
            if(index==0)continue;
            int color=(pal[index*2]&255)|((pal[index*2+1]&255)<<8);
            int r=color&31,g=(color>>5)&31,b=(color>>10)&31;
            pixels[i]=0xff000000|((r*255/31)<<16)|((g*255/31)<<8)|(b*255/31);
        }
        out.put(name,Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888));
    }
    static synchronized Bitmap icon(Context context,int character,int slot,int typeId){
        if(!ready){start(context);return null;}
        if(slot==0){int template=-1-typeId;
            return template>=0&&template<22?icons.get("physical-"+template):null;}
        return character>=0&&character<5&&slot>=1&&slot<=2
            ?icons.get("special-"+character+"-"+slot):null;
    }
}
