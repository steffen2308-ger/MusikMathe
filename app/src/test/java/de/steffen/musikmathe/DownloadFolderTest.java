package de.steffen.musikmathe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowContentResolver;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class DownloadFolderTest {
    private Context app;
    private File root;
    private TestDocuments provider;
    private final Uri first=DocumentsContract.buildTreeDocumentUri("folders.test","first"),second=DocumentsContract.buildTreeDocumentUri("folders.test","second");
    private static final int FLAGS=Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();
        ResolveInfo picker=new ResolveInfo();picker.activityInfo=new ActivityInfo();picker.activityInfo.name="PickerActivity";picker.activityInfo.packageName="folders.test";picker.activityInfo.exported=true;
        shadowOf(app.getPackageManager()).addResolveInfoForIntent(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),picker);
        new File(app.getFilesDir(),"video-download-folder.json").delete();
        root=Files.createTempDirectory("remembered-folder").toFile();assertTrue(new File(root,"first").mkdir());assertTrue(new File(root,"second").mkdir());
        provider=new TestDocuments(root);ProviderInfo info=new ProviderInfo();info.authority="folders.test";info.exported=true;info.grantUriPermissions=true;info.readPermission=info.writePermission="android.permission.MANAGE_DOCUMENTS";provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(info.authority,provider);
    }
    @After public void cleanup(){remove(root);}
    private void remove(File file){if(file.isDirectory())for(File child:file.listFiles())remove(child);file.delete();}
    @Test public void persistsFolderAndNeverOverwritesAnExistingVideo()throws Exception{
        DownloadFolder.remember(app,first,FLAGS);
        assertEquals(first,DownloadFolder.selected(app.createPackageContext(app.getPackageName(),0)));
        assertTrue(app.getContentResolver().getPersistedUriPermissions().stream().anyMatch(p->p.getUri().equals(first)&&p.isWritePermission()));
        Uri one=DownloadFolder.create(app,DownloadFolder.selected(app),"video/mp4","Same video.mp4");
        try(OutputStream output=app.getContentResolver().openOutputStream(one)){output.write("keep original".getBytes(StandardCharsets.UTF_8));}
        Uri two=DownloadFolder.create(app,DownloadFolder.selected(app),"video/mp4","Same video.mp4");assertNotEquals(one,two);
        assertEquals("keep original",new String(Files.readAllBytes(new File(root,"first/Same video.mp4").toPath()),StandardCharsets.UTF_8));assertTrue(new File(root,"first/Same video (2).mp4").exists());
        DownloadFolder.remember(app,second,FLAGS);assertEquals(second,DownloadFolder.selected(app));
        Uri later=DownloadFolder.create(app,DownloadFolder.selected(app),"video/mp4","Later.mp4");assertTrue(later.toString().contains("second"));
        try(OutputStream output=app.getContentResolver().openOutputStream(two)){output.write(42);}
        assertTrue("Changing folder must retain access for queued files",app.getContentResolver().getPersistedUriPermissions().stream().anyMatch(p->p.getUri().equals(first)&&p.isWritePermission()));
    }
    @Test @Config(sdk=23) public void directorySelectionAlsoWorksOnAndroid6()throws Exception{
        DownloadFolder.remember(app,first,FLAGS);assertEquals(first,DownloadFolder.selected(app));
        assertNotNull(DownloadFolder.create(app,DownloadFolder.selected(app),"video/mp4","Android6.mp4"));
        assertTrue(new File(root,"first/Android6.mp4").exists());
    }
    @Test public void inaccessibleFolderFailsAndRejectedChoiceRetainsPreviousFolder()throws Exception{
        DownloadFolder.remember(app,first,FLAGS);
        assertThrows(Exception.class,()->DownloadFolder.remember(app,second,Intent.FLAG_GRANT_READ_URI_PERMISSION));assertEquals(first,DownloadFolder.selected(app));
        provider.writable=false;assertThrows(Exception.class,()->DownloadFolder.create(app,first,"video/mp4","Blocked.mp4"));assertEquals(0,new File(root,"first").listFiles().length);
    }
    @Test public void cancellingQueuedFileRetainsPermissionForFutureVideos()throws Exception{
        DownloadFolder.remember(app,first,FLAGS);Uri output=DownloadFolder.create(app,first,"video/mp4","cancel.mp4");
        org.robolectric.android.controller.ServiceController<VideoDownloadService> controller=Robolectric.buildService(VideoDownloadService.class).create();
        try{
            controller.get().onStartCommand(new Intent().putExtra("url","https://example.org/video.mp4").putExtra("type","MP4").putExtra("headers","{}").putExtra("destination",output.toString()).putExtra("original",true),0,1);
            VideoDownloadService.cancelAll();assertFalse(new File(root,"first/cancel.mp4").exists());
            assertTrue(app.getContentResolver().getPersistedUriPermissions().stream().anyMatch(p->p.getUri().equals(first)&&p.isWritePermission()));
            assertNotNull(DownloadFolder.create(app,first,"video/mp4","next.mp4"));
        }finally{controller.destroy();shadowOf(Looper.getMainLooper()).idle();}
    }
    @Test public void eachPrivateAreaRemembersItsOwnGrantedVideoFolder()throws Exception{
        DownloadFolder.remember(app,first,FLAGS);
        try{
            BrowserApplication.privateProcess=true;
            PrivateProfiles.Session a=PrivateProfiles.probe(app,"Folder-A-password".toCharArray());PrivateProfiles.active=a;assertNull(DownloadFolder.selected(app));DownloadFolder.remember(app,second,FLAGS);assertEquals(second,DownloadFolder.selected(app));a.close();
            PrivateProfiles.Session b=PrivateProfiles.probe(app,"Folder-B-password".toCharArray());PrivateProfiles.active=b;assertNull(DownloadFolder.selected(app));DownloadFolder.remember(app,first,FLAGS);assertEquals(first,DownloadFolder.selected(app));b.close();
            PrivateProfiles.Session again=PrivateProfiles.probe(app,"Folder-A-password".toCharArray());PrivateProfiles.active=again;assertEquals(second,DownloadFolder.selected(app));assertNotNull(DownloadFolder.create(app,second,"video/mp4","Private A video.mp4"));again.close();
        }finally{if(PrivateProfiles.active!=null)PrivateProfiles.active.close();BrowserApplication.privateProcess=false;}
        assertEquals(first,DownloadFolder.selected(app));
    }
    private void choose(MainActivity activity,VideoCatalog.Item item)throws Exception{
        Method method=MainActivity.class.getDeclaredMethod("chooseVideoFile",VideoCatalog.Item.class,boolean.class,long.class);method.setAccessible(true);method.invoke(activity,item,false,0L);
    }
    private Intent downloadIntent()throws Exception{
        AtomicReference<Intent> found=new AtomicReference<>();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
        while(System.nanoTime()<end){shadowOf(Looper.getMainLooper()).idle();Intent next=shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();if(next!=null){found.set(next);break;}Thread.sleep(10);}
        assertNotNull("Expected queued video download",found.get());return found.get();
    }
    @Test public void firstVideoChoosesFolderAndNextVideoAfterRelaunchSkipsPicker()throws Exception{
        VideoCatalog catalog=new VideoCatalog();catalog.add("https://example.org/movie.mp4","video/mp4",Collections.emptyMap());VideoCatalog.Item item=catalog.list().get(0);
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();MainActivity activity=controller.get();
        try{
            choose(activity,item);AlertDialog prompt=ShadowAlertDialog.getLatestAlertDialog();prompt.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();
            org.robolectric.shadows.ShadowActivity.IntentForResult pick=shadowOf(activity).getNextStartedActivityForResult();assertNotNull("Folder picker should open after confirming the dialog",pick);assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE,pick.intent.getAction());
            assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE,shadowOf(activity).getNextStartedActivity().getAction());
            activity.onActivityResult(pick.requestCode,Activity.RESULT_OK,new Intent().setData(first).addFlags(FLAGS));Intent firstDownload=downloadIntent();
            controller.pause().stop().destroy();controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();
            choose(activity,item);Intent secondDownload=downloadIntent();assertNull(shadowOf(activity).getNextStartedActivity());assertNotEquals(firstDownload.getStringExtra("destination"),secondDownload.getStringExtra("destination"));
        }finally{controller.pause().stop().destroy();}
    }
    public static final class TestDocuments extends DocumentsProvider {
        final File root;boolean writable=true;
        TestDocuments(File root){this.root=root;}
        @Override public boolean onCreate(){return true;}
        @Override public boolean isChildDocument(String parent,String child){return child.startsWith(parent+"/");}
        @Override public Cursor queryRoots(String[] projection){return new MatrixCursor(new String[]{DocumentsContract.Root.COLUMN_ROOT_ID});}
        @Override public Cursor queryDocument(String id,String[] projection){MatrixCursor result=new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME});result.addRow(new Object[]{new File(root,id).getName()});return result;}
        @Override public Cursor queryChildDocuments(String parent,String[] projection,String sort){MatrixCursor result=new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME});File[] children=new File(root,parent).listFiles();if(children!=null)for(File child:children)result.addRow(new Object[]{child.getName()});return result;}
        @Override public String createDocument(String parent,String mime,String name)throws java.io.FileNotFoundException{
            if(!writable)throw new java.io.FileNotFoundException("Folder unavailable");try{File file=new File(root,parent+"/"+name);if(!file.createNewFile())throw new java.io.IOException("Exists");return parent+"/"+name;}catch(Exception e){throw new java.io.FileNotFoundException(e.getMessage());}
        }
        @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws java.io.FileNotFoundException{return ParcelFileDescriptor.open(new File(root,id),ParcelFileDescriptor.parseMode(mode));}
        @Override public void deleteDocument(String id){File file=new File(root,id);if(file.exists())assertTrue(file.delete());}
    }
}
