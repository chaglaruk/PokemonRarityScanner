package com.pokerarity.scanner.ui.permission;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.pokerarity.scanner.service.OverlayService;
import com.pokerarity.scanner.service.ScreenCaptureManager;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u0000 \n2\u00020\u0001:\u0001\nB\u0005\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0006\u001a\u00020\u00072\b\u0010\b\u001a\u0004\u0018\u00010\tH\u0014R\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000b"}, d2 = {"Lcom/pokerarity/scanner/ui/permission/ProjectionPermissionActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "mediaProjectionLauncher", "Landroidx/activity/result/ActivityResultLauncher;", "Landroid/content/Intent;", "onCreate", "", "savedInstanceState", "Landroid/os/Bundle;", "Companion", "app_debug"})
public final class ProjectionPermissionActivity extends androidx.appcompat.app.AppCompatActivity {
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_AUTO_CAPTURE = "extra_auto_capture";
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> mediaProjectionLauncher = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.ui.permission.ProjectionPermissionActivity.Companion Companion = null;
    
    public ProjectionPermissionActivity() {
        super();
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/pokerarity/scanner/ui/permission/ProjectionPermissionActivity$Companion;", "", "()V", "EXTRA_AUTO_CAPTURE", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}