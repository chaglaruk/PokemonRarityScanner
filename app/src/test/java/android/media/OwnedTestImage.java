package android.media;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import java.nio.ByteBuffer;

/** Test-only RGBA packet for the real capture method under a Robolectric reader shadow. */
public final class OwnedTestImage extends Image {
    private final Bitmap bitmap = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888);
    private final ByteBuffer pixels = ByteBuffer.allocate(bitmap.getByteCount());
    public OwnedTestImage() { bitmap.eraseColor(0xff888888); bitmap.copyPixelsToBuffer(pixels); pixels.rewind(); }
    @Override public int getWidth() { return 64; }
    @Override public int getHeight() { return 128; }
    @Override public int getFormat() { return PixelFormat.RGBA_8888; }
    @Override public long getTimestamp() { return 0; }
    @Override public Plane[] getPlanes() { return new Plane[]{new Plane() {
        @Override public ByteBuffer getBuffer() { return pixels; }
        @Override public int getRowStride() { return 64 * 4; }
        @Override public int getPixelStride() { return 4; }
    }}; }
    @Override public void close() { bitmap.recycle(); }
}
