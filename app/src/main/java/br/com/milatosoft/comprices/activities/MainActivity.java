package br.com.milatosoft.comprices.activities;


import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.location.places.PlaceLikelihoodBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

import br.com.milatosoft.comprices.R;
import br.com.milatosoft.comprices.factories.LocalFactory;
import br.com.milatosoft.comprices.pojo.MercadoPojo;


public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        GoogleApiClient.ConnectionCallbacks {


    private FirebaseAuth firebaseAuth;
    private FirebaseUser firebaseUser;
    private GoogleApiClient mGoogleApiClient;


    private TextView tvUsuario;
    private TextView tvLugar;
    private TextView tvNomeLugarAtual;
    private ArrayList<MercadoPojo> mercadosProximos = new ArrayList<>();
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);

        boolean logado = verificacaoDeLogin();
        Log.d("googleSigin", "logado " + logado);

        if (logado) {
            configuraElementos();


            //Texto com o nome do usuario
            tvUsuario.setText(firebaseAuth.getCurrentUser().getDisplayName());
            callConnection();
        }


    }

    private synchronized void callConnection() {
        progressBar.setVisibility(View.VISIBLE);
        mGoogleApiClient = new GoogleApiClient
                .Builder(this)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .addApi(LocationServices.API)
                .enableAutoManage(this, this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

    }

    @Override
    /**
     * Se conectou ao Firebase entao verifica as permissoes
     */
    public void onConnected(@Nullable Bundle bundle) {
        if (Build.VERSION.SDK_INT > 22) {
            String[] allPermissionNeeded = {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.INTERNET,
                    Manifest.permission.GET_ACCOUNTS,

            };

            List<String> permissionNeeded = new ArrayList<>();
            for (String permission : allPermissionNeeded)
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
                    permissionNeeded.add(permission);
            //Se nao tem todas as permissoes entao eh efetuado o pedido.
            if (permissionNeeded.size() > 0) {
                requestPermissions(permissionNeeded.toArray(new String[0]), 0);
                //o metodo que captura a localizacao atual quando o app ainda ainda nao tem autorizacao
                // eh chamado no onRequestPermissionsResult
            }
            //se tem todas as permissoes entao vai buscar a localizacao.
            else {

                getLocalAtual();
            }
        }
        progressBar.setVisibility(View.GONE);


    }

    /**
     * Captura o local atual da pessoa.
     */
    private void getLocalAtual() {
        progressBar.setVisibility(View.VISIBLE);
        final LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000 * 60 * 30);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());
        final MainActivity m = this;

        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                Log.d("Lclz", "Sucesso da atividade");
                if (ActivityCompat.checkSelfPermission(m, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("Lclz", "Não tem permissão");
                    return;
                }
                pesquisaAPIPlaceAtual(Places.PlaceDetectionApi.getCurrentPlace(mGoogleApiClient, null));

            }
        });

        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                int statusCode = ((ApiException) e).getStatusCode();
                switch (statusCode) {
                    case CommonStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            Log.d("Lclz", "Erro no try");
                            ResolvableApiException resolvable = (ResolvableApiException) e;
                            resolvable.startResolutionForResult(MainActivity.this,
                                    200);

                        } catch (IntentSender.SendIntentException sendEx) {
                            // Ignore the error.
                            Log.d("Lclz", sendEx.getMessage());
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        Log.d("Lclz", "Canal indisponivel");
                        // Location settings are not satisfied. However, we have no way
                        // to fix the settings so we won't show the dialog.
                        break;
                }
            }
        });
        progressBar.setVisibility(View.GONE);


    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        //200 - Chamada que ocorre quando o GPS esta desligado.
        if (requestCode == 200) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            pesquisaAPIPlaceAtual(Places.PlaceDetectionApi.getCurrentPlace(mGoogleApiClient, null));

        }
        else {
            Log.d("Lclz", "requestCode nao eh 200");
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        getLocalAtual();
    }




    final private int REQUEST_CODE_ASK_PERMISSIONS = 123;


    private void pedePermissaoLeituraGPS() {
        int hasWriteContactsPermission = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        if (hasWriteContactsPermission != PackageManager.PERMISSION_GRANTED) {
            Log.d("lclz", "Pedindo permissão");

            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                showMessageOKCancel(getString(R.string.str_confirm_lclz),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        REQUEST_CODE_ASK_PERMISSIONS);

                            }
                        });
                return;
            }
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_CODE_ASK_PERMISSIONS);
            return;
        }

    }

    private void showListDialog(String message, DialogInterface.OnClickListener metodoChamadoNoCliqueSobreALinha) {
        final String[] values = new String[mercadosProximos.size()];
        for (int i = 0; i < mercadosProximos.size(); i++) {
            values[i] = mercadosProximos.get(i).getNmMercado();
        }
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(message)
                .setNegativeButton("Cancel", null)
                .setItems(values, metodoChamadoNoCliqueSobreALinha)
                .create()
                .show();
    }


    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }
    private void pesquisaAPIPlaceAtual(PendingResult<PlaceLikelihoodBuffer> result) {
        result.setResultCallback(new ResultCallback<PlaceLikelihoodBuffer>() {
            @Override
            public void onResult(PlaceLikelihoodBuffer likelyPlaces) {
                StringBuffer lugares = new StringBuffer("");
                mercadosProximos= LocalFactory.mercadosPossiveis(likelyPlaces);
                if (mercadosProximos.isEmpty()){
                    tvNomeLugarAtual.setText("Lugar não encontrado");
                    lugares.append("Tem certeza que está em um supermercado? \n\nIremos verificar o que aconteceu com nossos servidores.");
                }
                else{
                    updateUI(0);
                }
                tvLugar.setText(lugares.toString());
                if(mercadosProximos.size()>=2){
                    configuraAlteracaoLocal();
                }
                likelyPlaces.release();
            }
        });
    }



    /**
     * Atribuicao dos elementos da tela as variaveis.
     */
    private void configuraElementos() {
        tvUsuario = (TextView) findViewById(R.id.id_nm_usu);
        tvLugar = (TextView) findViewById(R.id.id_tx_local);
        tvNomeLugarAtual = (TextView) findViewById(R.id.id_tv_lugar_atual);
        tvNomeLugarAtual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                configuraAlteracaoLocal();
            }
        });
        progressBar = (ProgressBar) findViewById(R.id.id_pb_main);


    }

    private void configuraAlteracaoLocal(){

        String[] values = new String[mercadosProximos.size()];
        for (int i =0; i< mercadosProximos.size();i++){
            values[i] = mercadosProximos.get(i).getNmMercado();
        }
        showListDialog(getString(R.string.str_pergunta_estabelecimento), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                updateUI(which);
            }
        });
    }


    private void updateUI(int iItemListaMercadosProximos){
        tvNomeLugarAtual.setText(mercadosProximos.get(iItemListaMercadosProximos).getNmMercado().toString());

    }

    /**
     * Pede para logar caso nao esteja logado.
     */
    private boolean verificacaoDeLogin() {
        firebaseAuth = FirebaseAuth.getInstance();

        if ((firebaseUser = firebaseAuth.getCurrentUser()) == null) {
            Log.d("googleSigin", "firebaseAuth.getCurrentUser() == null : " + String.valueOf(firebaseAuth.getCurrentUser() == null));
            Intent myIntent = new Intent(MainActivity.this, LoginActivity.class);
            MainActivity.this.startActivity(myIntent);
            finish();
            return false;
        }
        else{
            Log.d("googleSigin", "logado com sucesso"  );
            return true;
        }


    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i("Main", connectionResult.getErrorMessage());
        tvLugar.setText("Erro para pegar o lugar\n" + connectionResult.getErrorMessage());
    }

    @Override
    public void onLocationChanged(Location location) {
        pedePermissaoLeituraGPS();
    }


    @Override
    public void onConnectionSuspended(int i) {
        Log.i("Main", Integer.toString(i));

    }
}
