package com.example.ar_lab5

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.example.ar_lab5.data.AppDatabase
import com.example.ar_lab5.data.GeoMarker
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.launch

@Composable
fun ARScreen() {
    val context = LocalContext.current

    // -----------------------------
    // Room
    // -----------------------------

    val database = remember {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ar_lab5.db"
        ).build()
    }

    val dao = database.geoMarkerDao()
    val coroutineScope = rememberCoroutineScope()

    // -----------------------------
    // SceneView
    // -----------------------------

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    val squirrelModel = rememberModelInstance(
        modelLoader,
        "squirrel.glb"
    )

    // -----------------------------
    // AR state
    // -----------------------------

    var earthTrackingState by remember {
        mutableStateOf(TrackingState.STOPPED)
    }

    var latitude by remember {
        mutableStateOf<Double?>(null)
    }

    var longitude by remember {
        mutableStateOf<Double?>(null)
    }

    var altitude by remember {
        mutableStateOf<Double?>(null)
    }

    var currentSession by remember {
        mutableStateOf<Session?>(null)
    }

    // Последние сохранённые метки,
    // которые будут отображаться в AR.
    val markers = remember {
        mutableStateListOf<GeoMarker>()
    }

    // Anchor для каждой отображаемой метки.
    val anchors = remember {
        mutableStateMapOf<Long, Anchor>()
    }

    // Чтобы мы не загружали метки из БД каждый кадр.
    var markersLoaded by remember {
        mutableStateOf(false)
    }

    // -----------------------------
    // Настройки лабораторной
    // -----------------------------

    val maxDistanceMeters = 100f
    val maxMarkersCount = 5

    // -----------------------------
    // AR Scene
    // -----------------------------

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            planeRenderer = false,

            sessionConfiguration = { _, config ->
                config.geospatialMode =
                    Config.GeospatialMode.ENABLED
            },

            onSessionUpdated = { session, _ ->
                currentSession = session

                val earth = session.earth
                    ?: return@ARSceneView

                earthTrackingState = earth.trackingState

                if (earth.trackingState == TrackingState.TRACKING) {

                    val pose = earth.cameraGeospatialPose

                    latitude = pose.latitude
                    longitude = pose.longitude
                    altitude = pose.altitude
                }
            }
        ) {

            /*
             * Отображаем AnchorNode для каждой
             * сохранённой геометки.
             *
             * AnchorNode и ModelNode здесь являются
             * Compose-ноды SceneView.
             */
            anchors.forEach { (_, anchor) ->

                AnchorNode(
                    anchor = anchor
                ) {
                    SphereNode(
                        radius = 0.2f
                    )
                }
            }
        }

        // -----------------------------
        // UI
        // -----------------------------

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .background(
                    Color.Black.copy(alpha = 0.7f)
                )
                .padding(16.dp)
        ) {

            Text(
                text = "Earth: $earthTrackingState",
                color = Color.White
            )

            Text(
                text = "Lat: ${latitude ?: "-"}",
                color = Color.White
            )

            Text(
                text = "Lng: ${longitude ?: "-"}",
                color = Color.White
            )

            Text(
                text = "Alt: ${altitude ?: "-"}",
                color = Color.White
            )

            Text(
                text = "Метка: ${markers.size}/$maxMarkersCount",
                color = Color.White
            )

            Text(
                text = "Радиус: ${maxDistanceMeters.toInt()} м",
                color = Color.White
            )

            Button(
                enabled =
                    earthTrackingState == TrackingState.TRACKING &&
                            squirrelModel != null,

                onClick = {

                    val session = currentSession
                        ?: return@Button

                    val earth = session.earth
                        ?: return@Button

                    val currentLatitude = latitude
                        ?: return@Button

                    val currentLongitude = longitude
                        ?: return@Button

                    val currentAltitude = altitude
                        ?: return@Button

                    coroutineScope.launch {

                        // -----------------------------
                        // 1. Сохраняем метку в Room
                        // -----------------------------

                        val marker = GeoMarker(
                            latitude = currentLatitude,
                            longitude = currentLongitude,
                            altitude = currentAltitude,
                            name = "Метка"
                        )

                        dao.insert(marker)

                        // -----------------------------
                        // 2. Создаём Geospatial Anchor
                        // -----------------------------

                        val anchor = earth.createAnchor(
                            currentLatitude,
                            currentLongitude,
                            currentAltitude,
                            0f,
                            0f,
                            0f,
                            1f
                        )

                        // -----------------------------
                        // 3. Добавляем в отображаемые
                        // -----------------------------

                        markers.add(marker)

                        anchors[marker.id] = anchor
                    }
                }
            ) {
                Text("Добавить метку")
            }
        }
    }

    // -----------------------------
    // Загрузка последних меток
    // -----------------------------

    LaunchedEffect(
        earthTrackingState,
        currentSession,
        latitude,
        longitude,
        altitude
    ) {

        if (
            earthTrackingState != TrackingState.TRACKING ||
            currentSession == null ||
            latitude == null ||
            longitude == null ||
            altitude == null ||
            markersLoaded
        ) {
            return@LaunchedEffect
        }

        val earth = currentSession?.earth
            ?: return@LaunchedEffect

        val currentLatitude = latitude!!
        val currentLongitude = longitude!!

        // -----------------------------
        // Получаем последние метки
        // -----------------------------

        val savedMarkers = dao.getLastMarkers(
            maxMarkersCount
        )

        // -----------------------------
        // Отсекаем слишком далёкие
        // -----------------------------

        val nearbyMarkers = savedMarkers.filter { marker ->

            val distance = FloatArray(1)

            Location.distanceBetween(
                currentLatitude,
                currentLongitude,
                marker.latitude,
                marker.longitude,
                distance
            )

            distance[0] <= maxDistanceMeters
        }

        // -----------------------------
        // Создаём Anchor для каждой
        // подходящей метки
        // -----------------------------

        nearbyMarkers.forEach { marker ->

            val anchor = earth.createAnchor(
                marker.latitude,
                marker.longitude,
                marker.altitude,
                0f,
                0f,
                0f,
                1f
            )

            markers.add(marker)
            anchors[marker.id] = anchor
        }

        markersLoaded = true
    }
}

//@Composable
//fun ARScreen() {
//    val context = LocalContext.current
//
//    val database = remember {
//        Room.databaseBuilder(
//            context,
//            AppDatabase::class.java,
//            "ar_lab5.db"
//        ).build()
//    }
//    val dao = database.geoMarkerDao()
//    val coroutineScope = rememberCoroutineScope()
//
//    val engine = rememberEngine()
//    val modelLoader = rememberModelLoader(engine)
//
//    val squirrelModel = rememberModelInstance(
//        modelLoader,
//        "squirrel.glb"
//    )
//
//    var earthTrackingState by remember {
//        mutableStateOf(STOPPED)
//    }
//
//    var latitude by remember {
//        mutableStateOf<Double?>(null)
//    }
//
//    var longitude by remember {
//        mutableStateOf<Double?>(null)
//    }
//
//    var altitude by remember {
//        mutableStateOf<Double?>(null)
//    }
//
//    val anchorNodes = remember {
//        mutableStateListOf<AnchorNode>()
//    }
//    var currentSession by remember {
//        mutableStateOf<Session?>(null)
//    }
//
//    Box(
//        modifier = Modifier.fillMaxSize()
//    ) {
//
//        ARSceneView(
//            modifier = Modifier.fillMaxSize(),
//            planeRenderer = false,
//
//            sessionConfiguration = { session, config ->
//
//                config.geospatialMode =
//                    Config.GeospatialMode.ENABLED
//            },
//
//            onSessionUpdated = { session, frame ->
//                currentSession = session
//
//                val earth = session.earth ?: return@ARSceneView
//
//                earthTrackingState = earth.trackingState
//
//                if (earth.trackingState == TrackingState.TRACKING) {
//                    val pose = earth.cameraGeospatialPose
//
//                    latitude = pose.latitude
//                    longitude = pose.longitude
//                    altitude = pose.altitude
//                }
//            },
//        ) {
//            anchorNodes.forEach { anchorNode ->
//                Node { anchorNode }
//            }
//        }
//
//        Column(
//            modifier = Modifier
//                .align(Alignment.TopCenter)
//                .padding(16.dp)
//                .background(Color.Black.copy(alpha = 0.7f))
//                .padding(16.dp)
//        ) {
//
//            Text(
//                text = "Earth: $earthTrackingState",
//                color = Color.White
//            )
//
//            Text(
//                text = "Lat: ${latitude ?: "-"}",
//                color = Color.White
//            )
//
//            Text(
//                text = "Lng: ${longitude ?: "-"}",
//                color = Color.White
//            )
//
//            Text(
//                text = "Alt: ${altitude ?: "-"}",
//                color = Color.White
//            )
//
//            Button(
//                enabled = earthTrackingState == TrackingState.TRACKING,
//                onClick = {
//                    val session = currentSession ?: return@Button
//                    val earth = session.earth ?: return@Button
//
//                    val currentLatitude = latitude ?: return@Button
//                    val currentLongitude = longitude ?: return@Button
//                    val currentAltitude = altitude ?: return@Button
//
//                    coroutineScope.launch {
//                        dao.insert(
//                            GeoMarker(
//                                latitude = currentLatitude,
//                                longitude = currentLongitude,
//                                altitude = currentAltitude,
//                                name = "Метка"
//                            )
//                        )
//
//                        val anchor = earth.createAnchor(
//                            currentLatitude,
//                            currentLongitude,
//                            currentAltitude,
//                            0f,
//                            0f,
//                            0f,
//                            1f
//                        )
//
//                        val anchorNode = AnchorNode(
//                            engine = engine,
//                            anchor = anchor
//                        )
//
//                        anchorNode.addChildNode(
//                            ModelNode(
//                                modelInstance = squirrelModel!!,
//                                scaleToUnits = 0.5f
//                            )
//                        )
//
//                        anchorNodes.add(anchorNode)
//                    }
//                }
//            ) {
//                Text("Добавить метку")
//            }
//        }
//    }
//}