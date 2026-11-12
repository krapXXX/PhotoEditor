
package dev.arina.photoeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int CAMERA_REQUEST = 101;
    private static final int GALLERY_REQUEST = 102;

    private ImageView photoPreview;
    private TextView emptyText, statusText;

    private Bitmap originalBitmap;
    private Bitmap editedBitmap;

    private final ArrayDeque<Bitmap> history =
            new ArrayDeque<>();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final String DEMO_URL =
            "https://images.unsplash.com/photo-1500534623283-312aade485b7?w=1000";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        photoPreview = findViewById(R.id.photoPreview);
        emptyText = findViewById(R.id.emptyText);
        statusText = findViewById(R.id.statusText);

        findViewById(R.id.btnCamera).setOnClickListener(
                v -> openCamera());

        findViewById(R.id.btnGallery).setOnClickListener(
                v -> openGallery());

        findViewById(R.id.btnInternet).setOnClickListener(
                v -> showUrlDialog());

        findViewById(R.id.btnOriginal).setOnClickListener(
                v -> applyFilter("Original"));

        findViewById(R.id.btnBW).setOnClickListener(
                v -> applyFilter("B&W"));

        findViewById(R.id.btnSepia).setOnClickListener(
                v -> applyFilter("Sepia"));

        findViewById(R.id.btnVintage).setOnClickListener(
                v -> applyFilter("Vintage"));

        findViewById(R.id.btnCool).setOnClickListener(
                v -> applyFilter("Cool"));

        findViewById(R.id.btnWarm).setOnClickListener(
                v -> applyFilter("Warm"));

        findViewById(R.id.btnEnhance).setOnClickListener(
                v -> applyFilter("Enhance"));

        findViewById(R.id.btnRotate).setOnClickListener(
                v -> rotatePhoto());

        findViewById(R.id.btnMirror).setOnClickListener(
                v -> mirrorPhoto());

        findViewById(R.id.btnCrop).setOnClickListener(
                v -> cropPhoto());

        findViewById(R.id.btnUndo).setOnClickListener(
                v -> undo());

        findViewById(R.id.btnSave).setOnClickListener(
                v -> export(false));

        findViewById(R.id.btnShare).setOnClickListener(
                v -> export(true));
    }

    private void status(String message) {
        statusText.setText(message);
    }

    private boolean checkPhoto() {
        if (editedBitmap == null) {
            Toast.makeText(this,
                    "Спочатку додайте фотографію",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void setNewPhoto(Bitmap bitmap) {
        originalBitmap = bitmap;
        editedBitmap = bitmap;
        history.clear();
        showPhoto();
        status("Photo loaded successfully");
    }

    private void showPhoto() {
        photoPreview.setImageBitmap(editedBitmap);
        emptyText.setVisibility(View.GONE);
    }

    private void remember() {
        if (editedBitmap != null) {
            if (history.size() >= 10) {
                history.removeFirst();
            }
            history.addLast(editedBitmap);
        }
    }

    private void updatePhoto(Bitmap result, String message) {
        remember();
        editedBitmap = result;
        showPhoto();
        status(message);
    }

    // CAMERA
    private void openCamera() {
        Intent intent = new Intent(
                MediaStore.ACTION_IMAGE_CAPTURE);

        try {
            startActivityForResult(intent, CAMERA_REQUEST);
        } catch (Exception e) {
            status("Camera unavailable");
        }
    }

    // GALLERY
    private void openGallery() {
        Intent intent = new Intent(
                Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(
                Intent.createChooser(intent, "Choose photo"),
                GALLERY_REQUEST);
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(
                requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        if (requestCode == CAMERA_REQUEST) {
            Bundle extras = data.getExtras();

            if (extras != null) {
                Object value = extras.get("data");

                if (value instanceof Bitmap) {
                    setNewPhoto((Bitmap) value);
                }
            }
        }

        if (requestCode == GALLERY_REQUEST) {
            Uri uri = data.getData();

            if (uri != null) {
                loadGalleryPhoto(uri);
            }
        }
    }

    private void loadGalleryPhoto(Uri uri) {
        status("Opening gallery image...");

        executor.execute(() -> {
            try {
                Bitmap bitmap;

                try (InputStream stream =
                             getContentResolver().openInputStream(uri)) {

                    BitmapFactory.Options options =
                            new BitmapFactory.Options();

                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(
                            stream, null, options);

                    int sample = 1;

                    while (options.outWidth / sample > 1600
                            || options.outHeight / sample > 1600) {
                        sample *= 2;
                    }

                    options.inSampleSize = sample;
                    options.inJustDecodeBounds = false;

                    try (InputStream second =
                                 getContentResolver().openInputStream(uri)) {
                        bitmap = BitmapFactory.decodeStream(
                                second, null, options);
                    }
                }

                if (bitmap == null) {
                    throw new Exception("Invalid image");
                }

                Bitmap result = bitmap;
                runOnUiThread(() -> setNewPhoto(result));

            } catch (Exception e) {
                runOnUiThread(() ->
                        status("Failed to open image"));
            }
        });
    }

    // INTERNET + GLIDE
    private void showUrlDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(DEMO_URL);
        input.setSelectAllOnFocus(true);
        input.setPadding(32, 20, 32, 20);

        new AlertDialog.Builder(this)
                .setTitle("Import from Internet")
                .setMessage("Enter an image URL")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Load", (dialog, which) ->
                        loadInternetPhoto(
                                input.getText().toString().trim()))
                .show();
    }

    private void loadInternetPhoto(String url) {
        if (!url.startsWith("https://")) {
            status("Use a valid HTTPS image URL");
            return;
        }

        status("Downloading with Glide...");

        executor.execute(() -> {
            try {
                Bitmap bitmap = Glide.with(
                                getApplicationContext())
                        .asBitmap()
                        .load(url)
                        .submit(1600, 1600)
                        .get();

                runOnUiThread(() -> setNewPhoto(bitmap));

            } catch (Exception e) {
                runOnUiThread(() ->
                        status("Download failed. Check URL."));
            }
        });
    }

    // COLOR FILTERS
    private void applyFilter(String name) {
        if (!checkPhoto()) return;

        if (name.equals("Original")) {
            updatePhoto(originalBitmap, "Original image");
            return;
        }

        ColorMatrix matrix = new ColorMatrix();

        switch (name) {
            case "B&W":
                matrix.setSaturation(0);
                break;

            case "Sepia":
                matrix.set(new float[]{
                        .393f, .769f, .189f, 0, 0,
                        .349f, .686f, .168f, 0, 0,
                        .272f, .534f, .131f, 0, 0,
                        0, 0, 0, 1, 0
                });
                break;

            case "Vintage":
                matrix.setSaturation(.55f);
                ColorMatrix vintage = new ColorMatrix(
                        new float[]{
                                1.08f, 0, 0, 0, 12,
                                0, 1.02f, 0, 0, 5,
                                0, 0, .85f, 0, 0,
                                0, 0, 0, 1, 0
                        });
                matrix.postConcat(vintage);
                break;

            case "Cool":
                matrix.set(new float[]{
                        .92f, 0, 0, 0, 0,
                        0, 1.02f, 0, 0, 0,
                        0, 0, 1.20f, 0, 10,
                        0, 0, 0, 1, 0
                });
                break;

            case "Warm":
                matrix.set(new float[]{
                        1.16f, 0, 0, 0, 8,
                        0, 1.04f, 0, 0, 2,
                        0, 0, .86f, 0, 0,
                        0, 0, 0, 1, 0
                });
                break;

            case "Enhance":
                matrix.setSaturation(1.35f);
                ColorMatrix contrast = new ColorMatrix(
                        new float[]{
                                1.15f, 0, 0, 0, -12,
                                0, 1.15f, 0, 0, -12,
                                0, 0, 1.15f, 0, -12,
                                0, 0, 0, 1, 0
                        });
                matrix.postConcat(contrast);
                break;
        }

        Bitmap result = Bitmap.createBitmap(
                editedBitmap.getWidth(),
                editedBitmap.getHeight(),
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        paint.setColorFilter(
                new ColorMatrixColorFilter(matrix));

        canvas.drawBitmap(editedBitmap, 0, 0, paint);

        updatePhoto(result, "Filter applied: " + name);
    }

    // ROTATE
    private void rotatePhoto() {
        if (!checkPhoto()) return;

        Matrix matrix = new Matrix();
        matrix.postRotate(90);

        Bitmap result = Bitmap.createBitmap(
                editedBitmap,
                0, 0,
                editedBitmap.getWidth(),
                editedBitmap.getHeight(),
                matrix, true);

        updatePhoto(result, "Rotated 90 degrees");
    }

    // MIRROR
    private void mirrorPhoto() {
        if (!checkPhoto()) return;

        Matrix matrix = new Matrix();
        matrix.preScale(-1, 1);

        Bitmap result = Bitmap.createBitmap(
                editedBitmap,
                0, 0,
                editedBitmap.getWidth(),
                editedBitmap.getHeight(),
                matrix, true);

        updatePhoto(result, "Image mirrored");
    }

    // CENTER SQUARE CROP
    private void cropPhoto() {
        if (!checkPhoto()) return;

        int width = editedBitmap.getWidth();
        int height = editedBitmap.getHeight();
        int size = Math.min(width, height);

        if (width == height) {
            status("Image is already square");
            return;
        }

        int x = (width - size) / 2;
        int y = (height - size) / 2;

        Bitmap result = Bitmap.createBitmap(
                editedBitmap, x, y, size, size);

        updatePhoto(result, "Cropped to 1:1");
    }

    // UNDO
    private void undo() {
        if (history.isEmpty()) {
            status("Nothing to undo");
            return;
        }

        editedBitmap = history.removeLast();
        showPhoto();
        status("Last change undone");
    }

    // SAVE / SHARE
    private void export(boolean share) {
        if (!checkPhoto()) return;

        if (Build.VERSION.SDK_INT < 29) {
            status("Export requires Android 10+");
            return;
        }

        Bitmap snapshot = editedBitmap;
        status("Exporting image...");

        executor.execute(() -> {
            Uri uri = null;

            try {
                ContentValues values = new ContentValues();
                values.put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        "PhotoShop_" + System.currentTimeMillis() + ".jpg");
                values.put(
                        MediaStore.Images.Media.MIME_TYPE,
                        "image/jpeg");
                values.put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/PhotoShop2");
                values.put(
                        MediaStore.Images.Media.IS_PENDING, 1);

                uri = getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values);

                if (uri == null) {
                    throw new Exception("Cannot create image");
                }

                try (OutputStream out =
                             getContentResolver().openOutputStream(uri)) {

                    if (out == null || !snapshot.compress(
                            Bitmap.CompressFormat.JPEG, 95, out)) {
                        throw new Exception("Cannot write image");
                    }
                }

                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(
                        uri, done, null, null);

                Uri finalUri = uri;

                runOnUiThread(() -> {
                    status("Saved to Pictures/PhotoShop2");

                    if (share) {
                        Intent intent = new Intent(
                                Intent.ACTION_SEND);
                        intent.setType("image/jpeg");
                        intent.putExtra(
                                Intent.EXTRA_STREAM, finalUri);
                        intent.setClipData(
                                android.content.ClipData.newUri(
                                        getContentResolver(),
                                        "Edited photo", finalUri));
                        intent.addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        startActivity(Intent.createChooser(
                                intent, "Share edited photo"));
                    } else {
                        Toast.makeText(this,
                                "Photo saved!",
                                Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                if (uri != null) {
                    getContentResolver().delete(uri, null, null);
                }
                runOnUiThread(() ->
                        status("Export failed: " + e.getMessage()));
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
}
