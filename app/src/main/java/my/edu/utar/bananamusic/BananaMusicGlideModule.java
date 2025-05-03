package my.edu.utar.bananamusic;

import android.content.Context;
import android.util.Log;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

@GlideModule
public final class BananaMusicGlideModule extends AppGlideModule {
    private static final String TAG = "BananaMusicGlideModule";
    private static final int MEMORY_CACHE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final int DISK_CACHE_SIZE = 100 * 1024 * 1024;  // 100MB

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        // Memory cache configuration
        builder.setMemoryCache(new LruResourceCache(MEMORY_CACHE_SIZE));
        
        // Disk cache configuration
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, DISK_CACHE_SIZE));
        
        // Default request options
        builder.setDefaultRequestOptions(
            new RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565)
                .disallowHardwareConfig()
                .skipMemoryCache(false)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
        );

        // Enable verbose logging in debug mode
        if (BuildConfig.DEBUG) {
            builder.setLogLevel(Log.VERBOSE);
        }
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
} 