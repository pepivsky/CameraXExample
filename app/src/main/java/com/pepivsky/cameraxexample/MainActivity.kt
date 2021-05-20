package com.pepivsky.cameraxexample

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.labters.documentscanner.ImageCropActivity
import com.labters.documentscanner.helpers.ScannerConstants
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10 // codigo para verificar los permisos
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA) // arreglo de permisos

    }

    private var imageCapture: ImageCapture? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    //views en el layout
    lateinit var camera_capture_button: ImageButton //boton
    lateinit var viewFinder: PreviewView // previewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //findById
        camera_capture_button = findViewById(R.id.camera_capture_button)
        viewFinder  = findViewById(R.id.viewFinder)

        // Request camera permissions
        if (allPermissionsGranted()) { // reivisa si los permisos se han concedido, sino los solicita
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listener for take photo button
        camera_capture_button.setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    //funcion para tomar la foto
    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return // objeto de caso de uso de captura, si es nulo sale de la funcion

        // Create un archivo de salida con marca de tiempo (TimeStamp) en el nombre para guardar la foto y que el nombre sea unico
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg") //establece el formato de salida

        // Crea un objeto de opciones de salida a partir del archivo que recibe, en este objeto se pueden especificar mas opciones de salida
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build() // le pasamos el archivo en el que sera guardada la salida

        // Set up image capture listener, which is triggered after photo has been taken
        imageCapture.takePicture( //toma la foto, este metodo recibe las opciones de salida, un ejecutor y un callback que se llama cuando la imagen ha sido guardada
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) { // Callback se se llama cuando un error ocurre
                    Toast.makeText(baseContext, "Ha ocurrido un error :(", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) { //CallBack despues que la imagen es guardada
                    val savedUri = Uri.fromFile(photoFile) //obtiene el uri a partir del archivo recibido
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                // start image cropper
                    var selectedImage = savedUri //creando bitmap a partir del uri
                    var btimap: Bitmap? = null
                    try {
                        val inputStream = selectedImage?.let { contentResolver.openInputStream(it) }
                        btimap = BitmapFactory.decodeStream(inputStream)
                        ScannerConstants.selectedImageBitmap = btimap
                        // se lanza el intent para pasar al activity crop
                        startActivityForResult(
                            Intent(this@MainActivity, ImageCropActivity::class.java),
                            1234
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            })

    }

    // se recupera la imagen
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // recuperando la imagen recortada
        if (requestCode== 1234 && resultCode== Activity.RESULT_OK )
        {
            if (ScannerConstants.selectedImageBitmap!=null)
                //imgBitmap.setImageBitmap(ScannerConstants.selectedImageBitmap)
                    Log.d(TAG, "imagen recuperada ${ScannerConstants.selectedImageBitmap}")
            else
                Toast.makeText(MainActivity@this,"Something wen't wrong.",Toast.LENGTH_LONG).show()
        }
    }

    //funcion para tomar la foto
    private fun startCamera() { // aqui se configura el caso de uso de preview
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // definiendo la configuracion del objeto Preview
            val preview = Preview.Builder()
                .build() //inicializa el preview
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }
            //configurando el objeto de captura (caso de uso)
            imageCapture = ImageCapture.Builder()
                .build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera (la instancia resultante se vincula al ciclo de vida)
                // Este objeto recibe los casos de uso y los vincula
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture) //se le pasa el selector, el preview y el imageCapture

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {

        if (requestCode == REQUEST_CODE_PERMISSIONS) { //comprueba que el codigo es correcto
            if (allPermissionsGranted()) { // si todos los permisos se han concedido entonces inicia la camara
                startCamera()
            } else { // si lo permisos son rechazados lanza un toast
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }


}