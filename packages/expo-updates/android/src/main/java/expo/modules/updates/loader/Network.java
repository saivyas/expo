package expo.modules.updates.loader;

import android.content.Context;
import android.net.Uri;

import expo.modules.updates.UpdateUtils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;

import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Network {

  private static OkHttpClient sClient = new OkHttpClient.Builder().build();

  public interface FileDownloadCallback {
    void onFailure(Exception e);
    void onSuccess(File file, byte[] hash);
  }

  public static void downloadFileToPath(Request request, final File destination, final FileDownloadCallback callback) {
    downloadData(request, new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        callback.onFailure(e);
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        if (!response.isSuccessful()) {
          callback.onFailure(new Exception("Network request failed: " + response.body().string()));
          return;
        }

        try (
                InputStream inputStream = response.body().byteStream();
                DigestInputStream digestInputStream = new DigestInputStream(inputStream, MessageDigest.getInstance("SHA-1"));
        ) {
          FileUtils.copyInputStreamToFile(digestInputStream, destination);

          MessageDigest md = digestInputStream.getMessageDigest();
          byte[] data = md.digest();
          callback.onSuccess(destination, data);
        } catch (Exception e) {
          callback.onFailure(e);
        }
      }
    });
  }

  public static void downloadData(Request request, Callback callback) {
    sClient.newCall(request).enqueue(callback);
  }

  public static Request addHeadersToUrl(Uri url, Context context) {
    Request.Builder requestBuilder = new Request.Builder()
            .url(url.toString())
            .header("Expo-Platform", "android");

    String binaryVersion = UpdateUtils.getBinaryVersion(context);
    if (binaryVersion != null) {
      requestBuilder = requestBuilder.header("Expo-Binary-Version", binaryVersion)
              .header("Expo-SDK-Version", binaryVersion);
    }
    return requestBuilder.build();
  }

  public static Request addHeadersToManifestUrl(Uri url, Context context) {
    Request.Builder requestBuilder = new Request.Builder()
            .url(url.toString())
            .header("Accept", "application/expo+json,application/json")
            .header("Expo-Platform", "android")
            .header("Expo-JSON-Error", "true")
            .header("Expo-Accept-Signature", "true")
            .cacheControl(CacheControl.FORCE_NETWORK);

    String binaryVersion = UpdateUtils.getBinaryVersion(context);
    if (binaryVersion != null) {
      requestBuilder = requestBuilder.header("Expo-Binary-Version", binaryVersion)
              .header("Expo-SDK-Version", binaryVersion);
    }
    return requestBuilder.build();
  }
}
