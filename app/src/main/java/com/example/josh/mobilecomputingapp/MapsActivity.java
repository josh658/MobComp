package com.example.josh.mobilecomputingapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    //MAP VARS
    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallBack;
    private static final float DEFAULT_ZOOM = 15f;

    //RESTAURANT LOCATOR VAR
    private static int radius= 1000;


    //PERMISSION VARS
    private static final String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    private static final int REQUEST_CODE = 1234;
    private boolean mLocationPermissionsGranted = false;

    // DEBUGGING VAR
    private static final String TAG = "MapActivity";


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        Toast.makeText(this, "Map is Ready", Toast.LENGTH_LONG).show();
        mMap = googleMap;

        if (mLocationPermissionsGranted) {
            getDeviceLocation();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mMap.setMyLocationEnabled(true);


        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        getLocationPermission();


    }

    private void initMap() {
        Log.d(TAG, "initMap: map was initialized");
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }


    /*
    ------------------------------------Getting users Location--------------------------------------
     */
    private void getLocationPermission() {
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), permissions[0]) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this.getApplicationContext(), permissions[1]) == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionsGranted = true;
            initMap();
        } else {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        mLocationPermissionsGranted = false;

        switch (requestCode) {
            case REQUEST_CODE: {
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                            mLocationPermissionsGranted = false;
                            return;
                        }
                    }
                    mLocationPermissionsGranted = true;
                    initMap();
                }
            }
        }
    }

    private void getDeviceLocation() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        try {
            if (mLocationPermissionsGranted) {

                final Task location = mFusedLocationClient.getLastLocation();
                location.addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "onComplete: found location");
                             Location mCurrentLocation = (Location) task.getResult();

                            moveCamera(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()), DEFAULT_ZOOM);
                            StringBuilder sbValue = new StringBuilder(sbMethod(mCurrentLocation));
                            PlacesTask placesTask = new PlacesTask();
                            placesTask.execute(sbValue.toString());

                        } else {
                            Log.d(TAG, "onComplete: current Location null");
                            Toast.makeText(MapsActivity.this, "unable to get current location", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        } catch (SecurityException e) {

        }
    }

    private void moveCamera(LatLng latLng, Float zoom) {
        Log.d(TAG, "moveCamera: moving camera to " + latLng.latitude + " " + latLng.longitude);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
    }




    /*
    ------------------------------------Finding nearby restaurants----------------------------------
     */


    public StringBuilder sbMethod(Location mCurrentLocation){

        StringBuilder sb = new StringBuilder("https://maps.googleapis.com/maps/api/place/nearbysearch/json?");
        sb.append("location=" + mCurrentLocation.getLatitude() + "," + mCurrentLocation.getLongitude());
        sb.append("&radius=" + radius);
        sb.append("&type=restaurant");
        sb.append("&sensor=true");
        sb.append("&key=AIzaSyDLb-QtLNo4vaHBBPglmVSzoVGECCs6d_Y");

        Log.d(TAG, "StringBuilder: " + sb.toString());

        return sb;
    }

    private class PlacesTask extends AsyncTask<String, Integer, String>{
        String data = null;

        @Override
        protected String doInBackground(String... url) {
            try{
                data = downloadURL(url[0]);

            } catch (Exception e){
                Log.d(TAG, "PlaceTask: doInBackground =" + e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            ParserTask parserTask = new ParserTask();

            parserTask.execute(result);
        }
    }

    private String downloadURL(String stringURL) throws IOException{
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try{
            URL url = new URL(stringURL);

            urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.connect();

            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while((line = br.readLine()) != null){
                sb.append(line);
            }

            data = sb.toString();

            br.close();
        } catch (Exception e){
            Log.d(TAG, "DownloadURL: FAILURE while downloading URL" + e.toString() );

        }finally{
            iStream.close();
            urlConnection.disconnect();
        }
        Log.d(TAG,"DownloadURL: data = " + data.toString());
        return data;
    }

    private class ParserTask extends AsyncTask<String, Integer, List<HashMap<String, String>>>{
        JSONObject jObject;

        @Override
        protected List<HashMap<String, String>> doInBackground(String... jsonData) {
            List<HashMap<String, String>> places = null;
            Place_JSON placeJson = new Place_JSON();

            try{
                jObject = new JSONObject(jsonData[0]);
                places = placeJson.parse(jObject);

            }catch(Exception e){
                Log.d(TAG, "ParseTask: Error when parsing JSON file");
            }

            return places;
        }

        @Override
        protected void onPostExecute(List<HashMap<String, String>> list) {

            Log.d(TAG, "ParserTask: onPost; List size = " + list.size());
            mMap.clear();

            for(int i = 0; i < list.size(); i++){
                MarkerOptions markerOptions = new MarkerOptions();
                HashMap<String, String> hmPlace = list.get(i);
                double lat = Double.parseDouble(hmPlace.get("lat"));
                double lng = Double.parseDouble(hmPlace.get("lng"));
                String name = hmPlace.get("place_name");
                Log.d(TAG, "Place: " + name);
                String vicinity = hmPlace.get("vicinity");
                LatLng latLng = new LatLng(lat, lng);
                markerOptions.position(latLng);
                markerOptions.title(name + " : " + vicinity);
                markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
                Marker m = mMap.addMarker(markerOptions);
            }
        }
    }

    public class Place_JSON{
        public List<HashMap<String, String>> parse (JSONObject jObject){
            JSONArray jPlaces = null;
            try{
                jPlaces = jObject.getJSONArray("results");
            } catch (JSONException e){
                e.printStackTrace();
            }

            return getPlaces(jPlaces);
        }

        private List<HashMap<String, String>> getPlaces(JSONArray jPlaces){
            int placesCount = jPlaces.length();
            List<HashMap<String,String>> placesList = new ArrayList<HashMap<String, String>>();
            HashMap<String, String> place = null;

            for (int i =0; i < placesCount; i++){
                try{
                    place = getPlace((JSONObject) jPlaces.get(i));
                    placesList.add(place);
                } catch (JSONException e){
                    e.printStackTrace();
                }
            }
            return placesList;
        }

        private HashMap<String, String> getPlace(JSONObject jPlace){

            HashMap<String, String> place = new HashMap<String, String>();
            String placeName = "-NA-";
            String vicinity = "-NA-";
            String latitude = "";
            String longitude = "";
            String reference = "";

            try{
                if(!jPlace.isNull("name")){
                    placeName = jPlace.getString("name");
                }
                if(!jPlace.isNull("name")){
                    vicinity = jPlace.getString("vicinity");
                }

                latitude = jPlace.getJSONObject("geometry").getJSONObject("location").getString("lat");
                longitude = jPlace.getJSONObject("geometry").getJSONObject("location").getString("lng");
                reference = jPlace.getString("reference");

                place.put("place_name", placeName);
                place.put("vicinity", vicinity);
                place.put("lat", latitude);
                place.put("lng", longitude);
                place.put("reference", reference);
            }catch (JSONException e) {
                    e.printStackTrace();
            }
            return place;

        }
    }






    /*
    -----------------------------------Random Algorithm---------------------------------------------
     */
}
