package com.example.camera;

import android.content.ContentValues;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.widget.ProgressBar;
public class ImgCompressorActivity extends AppCompatActivity {

    private ImageView compressedImageView;
    private TextView compressedImageInfoTextView;
    private ProgressBar progressBar; // Spinner to show progress
    private Bitmap originalBitmap; // Keep reference for cleanup

    // Constants for image compression
    private static final int INITIAL_QUALITY = 100; // Initial quality
    private static final int MIN_QUALITY = 10; // Minimum quality before stopping compression
    private static final int MAX_COMPRESSED_SIZE_MB = 6; // Maximum size in MB
    private static final int MAX_COMPRESSED_SIZE_BYTES = MAX_COMPRESSED_SIZE_MB * 1024 * 1024; // Maximum size in bytes
    private static final int QUALITY_DECREMENT = 5; // Quality decrement in each iteration

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_image_compressor);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        compressedImageView = findViewById(R.id.compressedImage);
        compressedImageInfoTextView = findViewById(R.id.compressedImageInfoTextView);
        progressBar = findViewById(R.id.progressBar); // Initialize the ProgressBar

        String imageUriString = getIntent().getStringExtra("image_uri");
        String image_details = getIntent().getStringExtra("image_details");

        if (imageUriString != null) {
            Uri imageUri = Uri.parse(imageUriString);
            Log.e("ImageError", "Error loading or compressing image: " + imageUri);
            compressAndDisplayImage(imageUri, image_details);
        }
    }

    /**
     * Compresses the image and displays the compressed image along with its details.
     *
     * @param imageUri      The URI of the image to be compressed.
     * @param image_details The details of the original image.
     */
    private void compressAndDisplayImage(Uri imageUri, String image_details) {
        clearCache(); // Clear cache before starting
        runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE)); // Show the spinner

        new Thread(() -> {
            try {
                // Open the InputStream of the original image
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                originalBitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();

                // Compress the image until it is less than MAX_COMPRESSED_SIZE_MB
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int quality = INITIAL_QUALITY; // Starting quality
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);

                // Compression loop until the image size is less than MAX_COMPRESSED_SIZE_MB
                while (baos.toByteArray().length > MAX_COMPRESSED_SIZE_BYTES && quality > MIN_QUALITY) {
                    baos.reset(); // Clear the output stream
                    quality -= QUALITY_DECREMENT; // Reduce quality
                    originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                }

                // Convert the result to a Bitmap
                byte[] compressedBytes = baos.toByteArray();
                Bitmap compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.length);

                // Calculate the size of the compressed image in KB
                int compressedSizeInKB = compressedBytes.length / 1024;

                runOnUiThread(() -> {
                    compressedImageView.setImageBitmap(compressedBitmap);

                    // Display the properties of the original and compressed images
                    String details = "Original Image:\n" +
                            image_details +
                            "\n" +
                            "Compressed Image:\n" +
                            "Width: " + compressedBitmap.getWidth() + " px\n" +
                            "Height: " + compressedBitmap.getHeight() + " px\n" +
                            "Size: " + compressedSizeInKB + " KB";
                    compressedImageInfoTextView.setText(details);
                    compressedImageInfoTextView.setVisibility(View.VISIBLE);
                    saveCompressedImage(compressedBytes);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error loading or compressing image", Toast.LENGTH_SHORT).show());
            } finally {
                clearCache();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE); // Hide the spinner
                });
            }
        }).start();
    }

    /**
     * Saves the compressed image to the device's storage.
     *
     * @param compressedBytes The byte array of the compressed image.
     */
    private void saveCompressedImage(byte[] compressedBytes) {
        new Thread(() -> {
            try {
                String name = "IMG_COMPRESSED_" + System.currentTimeMillis() + ".jpg";
                Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                Uri imageUri = getContentResolver().insert(uri, values);

                FileOutputStream output = new FileOutputStream(getContentResolver().openFileDescriptor(imageUri, "w").getFileDescriptor());
                output.write(compressedBytes);
                output.close();

                MediaScannerConnection.scanFile(this, new String[]{imageUri.toString()}, null, null);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Compressed image saved: " + name, Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error saving compressed image", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * Clears the cache by recycling the original bitmap if it exists.
     */
    private void clearCache() {
        if (originalBitmap != null) {
            originalBitmap.recycle();
            originalBitmap = null;
        }
    }
}

