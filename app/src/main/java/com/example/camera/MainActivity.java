package com.example.camera;



import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private ImageReader imageReader;
    private Size imageDimension;
    private byte[] imageBytes;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.textureView);
        Button captureButton = findViewById(R.id.captureButton);
        captureButton.setOnClickListener(v -> takePicture());


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            openCamera();
        }
        clearCache();
    }

    private void openCamera() {
        // Obtén el CameraManager
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            imageDimension = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(SurfaceTexture.class)[0];

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);

            // Inicializar ImageReader aquí
            imageReader = ImageReader.newInstance(imageDimension.getWidth(), imageDimension.getHeight(), ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<>();
            outputSurfaces.add(surface);
            outputSurfaces.add(imageReader.getSurface());

            // Crear la sesión de captura
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSession = session;
                    // Configurar el builder para la vista previa
                    final CaptureRequest.Builder previewRequestBuilder;
                    try {
                        previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    } catch (CameraAccessException e) {
                        throw new RuntimeException(e);
                    }
                    previewRequestBuilder.addTarget(surface);

                    // Iniciar la vista previa
                    try {
                        cameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), null, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    showToast("Camera configuration failed");
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void takePicture() {
        if (cameraDevice == null) {
            showToast("Camera not ready");
            return;
        }

        try {
            if (imageReader == null) {
                imageReader = ImageReader.newInstance(imageDimension.getWidth(), imageDimension.getHeight(), ImageFormat.JPEG, 1);
            }

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);

            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        imageBytes = new byte[buffer.remaining()];
                        buffer.get(imageBytes);


                        int width = imageDimension.getWidth();
                        int height = imageDimension.getHeight();
                        int sizeInKB = imageBytes.length / 1024;

                        // Mostrar detalles de la imagen
                        TextView imageDetailsTextView = findViewById(R.id.imageDetailsTextView);
                        String details = "Width: " + width + " px\nHeight: " + height + " px\nSize: " + sizeInKB + " KB";
                        imageDetailsTextView.setText(details);
                        imageDetailsTextView.setVisibility(View.VISIBLE);
                        clearCache();
                        saveImage(imageBytes);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("CameraError", "Error capturing image: " + e.getMessage());
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            }, null);

            cameraCaptureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    showToast("Picture taken!");


                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.e("CameraError", "Camera access error: " + e.getMessage());
        }
    }


    public byte[] compressImageDynamically(Bitmap bitmap, int maxSizeMB) {
        int quality = 100; // Calidad inicial (máxima)
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream);

        // Convertir MB a bytes
        final int MAX_SIZE_BYTES = maxSizeMB * 1024 * 1024;

        // Reducir la calidad mientras el tamaño de la imagen comprimida sea mayor que el límite
        while (stream.toByteArray().length > MAX_SIZE_BYTES && quality > 0) {
            stream.reset();  // Limpiar el stream
            quality -= 5;    // Reducir la calidad en un 5% en cada iteración
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream); // Re-comprimir
        }

        return stream.toByteArray(); // Devolver la imagen comprimida como un array de bytes
    }

    private void saveImage(byte[] bytes) {
        try {
            String name = "IMG_" + System.currentTimeMillis() + ".jpg";
            Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            Uri imageUri = getContentResolver().insert(uri, values);

            FileOutputStream output = new FileOutputStream(getContentResolver().openFileDescriptor(imageUri, "w").getFileDescriptor());
            output.write(bytes);
            output.close();

            // Calcular el tamaño de la imagen en KB
            int width = imageDimension.getWidth();
            int height = imageDimension.getHeight();
            int sizeInKB = bytes.length / 1024;

            // Mostrar detalles de la imagen
            TextView imageDetailsTextView = findViewById(R.id.imageDetailsTextView);
            String details = "Width: " + width + " px\nHeight: " + height + " px\nSize: " + sizeInKB + " KB";
            imageDetailsTextView.setText(details);
            imageDetailsTextView.setVisibility(View.VISIBLE);

            // Mostrar Toast
            Toast.makeText(this, "Image saved: " + name, Toast.LENGTH_SHORT).show();

            // Pasar la URI a la siguiente actividad
            Intent intent = new Intent(this, ImgCompressorActivity.class);
            intent.putExtra("image_uri", imageUri.toString());
            intent.putExtra("image_details", details);


            startActivity(intent);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is needed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void clearCache() {
        File cacheDir = getCacheDir();
        if (cacheDir != null && cacheDir.isDirectory()) {
            for (File file : cacheDir.listFiles()) {
                file.delete();
            }
        }
    }
}