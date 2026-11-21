package org.erpiopio.legacygoku2.thor;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;

final class Legacy2ScouterResources {
    static final String SHA1="db36ff52fcd63f753f9d66439aa3d2216701c326";
    private static native void extractNative(String rom,String output) throws IOException;
    static File directory(Context c) {return new File(c.getFilesDir(),"scouter/"+SHA1+"/v1");}
    static JSONObject manifest(File dir) throws Exception {
        JSONObject m=new JSONObject(new String(Files.readAllBytes(new File(dir,"manifest.json").toPath()),StandardCharsets.UTF_8));
        if(!SHA1.equals(m.getString("romSha1"))||m.getInt("extractorVersion")!=1||m.getJSONArray("regions").length()!=68||m.getInt("roomCount")!=297)
            throw new IOException("Invalid ALFP Scouter manifest");
        return m;
    }
    static void validate(File dir) throws Exception {
        JSONObject m=manifest(dir);int count=0;
        if(new File(dir,"background.pal").length()!=512||new File(dir,"objects.pal").length()!=512||new File(dir,"player-marker.indices").length()!=64)throw new IOException("Missing Scouter palette or marker");
        JSONArray regions=m.getJSONArray("regions");
        for(int i=0;i<regions.length();++i){
            JSONObject g=regions.getJSONObject(i);
            if(g.getInt("id")!=i||!g.getString("graphics").equals("region_"+i+".tiles")||new File(dir,g.getString("graphics")).length()!=g.getInt("graphicsBytes"))throw new IOException("Invalid Scouter graphics");
            JSONArray rooms=g.getJSONArray("rooms");
            for(int j=0;j<rooms.length();++j){
                JSONObject room=rooms.getJSONObject(j);JSONArray b=room.getJSONArray("rectangles");
                int bytes=0;for(int k=0;k<8;k+=4)bytes+=(b.getInt(k+2)-b.getInt(k))*(b.getInt(k+3)-b.getInt(k+1))*2;
                String file="room_"+i+"_"+j+".tilemap";
                if(!file.equals(room.getString("tilemap"))||!new File(dir,file).isFile()||new File(dir,file).length()!=bytes)throw new IOException("Invalid Scouter tilemap");
                count++;
            }
        }
        if(count!=297)throw new IOException("Incomplete Scouter rooms");
    }
    static synchronized void prepare(Context c,File rom) throws Exception {
        File target=directory(c);
        try {validate(target);return;}catch(Exception missing) { /* generate below */ }
        MessageDigest digest=MessageDigest.getInstance("SHA-1");
        try(FileInputStream in=new FileInputStream(rom)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(rom.length()!=8388608||!SHA1.contentEquals(hash))throw new IOException("Scouter requires the verified ALFP ROM");
        System.loadLibrary("trilogy_legacy2_resources");
        File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create Scouter cache");
        File stage=new File(parent,"staging-"+java.util.UUID.randomUUID());
        if(!stage.mkdir())throw new IOException("Cannot stage Scouter extraction");
        extractNative(rom.getAbsolutePath(),stage.getAbsolutePath());validate(stage);
        if(target.exists()){
            File backup=new File(parent,"previous-"+System.currentTimeMillis());
            if(!target.renameTo(backup))throw new IOException("Cannot preserve previous Scouter resources");
            if(!stage.renameTo(target)){backup.renameTo(target);throw new IOException("Cannot activate Scouter resources");}
        }else if(!stage.renameTo(target))throw new IOException("Cannot activate Scouter resources");
    }
}
