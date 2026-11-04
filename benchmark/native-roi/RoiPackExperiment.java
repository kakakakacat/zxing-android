// SPDX-License-Identifier: Apache-2.0
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class RoiPackExperiment {
  private static native boolean open(String path);
  private static native int nativePack(ByteBuffer input, byte[] output,
      int width, int height, int row, int pixel, int left, int top);
  private static native void shutdown();
  private static native boolean arrayCopyProbe(byte[] output);
  // Mirrors YPlaneLuminanceSource's direct-buffer ROI path; arrays/cursors are not shared with JNI.
  // The runner disables inlining of this method to prevent a batch loop absorbing/eliding copies.
  private static void packJava(ByteBuffer input, byte[] out, int w, int h, int row,
      int pixel, int left, int top) {
    if (pixel == 1) {
      ByteBuffer cursor = input.duplicate();
      for (int y=0; y<h; y++) {
        cursor.position((top+y)*row+left);
        cursor.get(out, y*w, w);
      }
    } else {
      for (int y=0; y<h; y++) {
        int start = (top+y)*row+left*pixel;
        for (int x=0; x<w; x++) out[y*w+x] = input.get(start+x*pixel);
      }
    }
  }
  private static long batch(boolean nativeMode, int n, ByteBuffer src, byte[] dst,
      int w,int h,int row,int pixel,int left,int top) {
    long start=System.nanoTime();
    for (int i=0;i<n;i++) {
      if (nativeMode) {
        if (nativePack(src,dst,w,h,row,pixel,left,top)!=0) throw new AssertionError("native rejected input");
      } else packJava(src,dst,w,h,row,pixel,left,top);
    }
    return System.nanoTime()-start;
  }
  public static void main(String[] args) throws Exception {
    boolean nativeMode=!args[0].equals("java");
    if(nativeMode) {
      System.load(args[1]);
      if(!open(args[2])) throw new AssertionError("cannot load native kernel");
    }
    int samples=Integer.parseInt(args[4]);
    double warmMs=Double.parseDouble(args[5]), targetMs=Double.parseDouble(args[6]);
    try {
      for(String line:Files.readAllLines(Path.of(args[3]))) {
        String[] f=line.split("\\s+");
        int w=Integer.parseInt(f[3]), h=Integer.parseInt(f[4]), row=Integer.parseInt(f[5]),
            pixel=Integer.parseInt(f[6]), left=Integer.parseInt(f[7]), top=Integer.parseInt(f[8]);
        byte[] raw=Files.readAllBytes(Path.of(f[1])), expected=Files.readAllBytes(Path.of(f[2]));
        ByteBuffer src=ByteBuffer.allocateDirect(raw.length);
        src.put(raw).clear();
        byte[] dst=new byte[expected.length];
        batch(nativeMode,1,src,dst,w,h,row,pixel,left,top);
        if(!Arrays.equals(dst,expected)) throw new AssertionError("output mismatch before timing");
        int n=1;
        while(batch(nativeMode,n,src,dst,w,h,row,pixel,left,top)<targetMs*1e6 && n<(1<<22)) n*=2;
        long warmStart=System.nanoTime();
        while(System.nanoTime()-warmStart<warmMs*1e6) batch(nativeMode,n,src,dst,w,h,row,pixel,left,top);
        String copyProbe = nativeMode ? Boolean.toString(arrayCopyProbe(dst)) : "null";
        StringBuilder out=new StringBuilder("{\"case\":\"").append(f[0])
            .append("\",\"iterations\":").append(n)
            .append(",\"jni_array_copy_probe\":").append(copyProbe).append(",\"ns_per_copy\":[");
        for(int i=0;i<samples;i++) {
          double elapsed=(double)batch(nativeMode,n,src,dst,w,h,row,pixel,left,top)/n;
          if(!Arrays.equals(dst,expected)) throw new AssertionError("output mismatch after timing");
          if(src.position()!=0) throw new AssertionError("source cursor changed");
          if(i>0) out.append(','); out.append(elapsed);
        }
        System.out.println(out.append("]}"));
      }
    } finally { if(nativeMode) shutdown(); }
  }
}
