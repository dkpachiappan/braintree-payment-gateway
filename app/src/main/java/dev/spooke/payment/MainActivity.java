package dev.spooke.payment;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.GooglePayment;
import com.braintreepayments.api.PayPal;
import com.braintreepayments.api.dropin.DropInActivity;
import com.braintreepayments.api.dropin.DropInRequest;
import com.braintreepayments.api.dropin.DropInResult;
import com.braintreepayments.api.dropin.utils.PaymentMethodType;
import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.interfaces.HttpResponseCallback;
import com.braintreepayments.api.interfaces.TokenizationParametersListener;
import com.braintreepayments.api.internal.HttpClient;
import com.braintreepayments.api.models.GooglePaymentRequest;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.google.android.gms.wallet.CardRequirements;
import com.google.android.gms.wallet.Cart;
import com.google.android.gms.wallet.LineItem;
import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.PaymentDataRequest;
import com.google.android.gms.wallet.PaymentMethodTokenizationParameters;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.WalletConstants;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE =1 ;
    private static final int GOOGLE_PAYMENT_REQUEST =2;

    /*This API's from local sever*/

    final String API_GET_TOKEN ="http://10.0.0.2/braintree/main.php";
    final String API_CHECK_OUT ="http://10.0.0.2/braintree/checkout.php";

    BraintreeFragment mBraintreeFragment;


    String token,amount;
    HashMap<String,String> paramsHash;

    EditText edt_amount;
    Button btn_pay;
    LinearLayout waiting_group,payment_group;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        waiting_group=(LinearLayout)findViewById(R.id.waiting_group);
        payment_group=(LinearLayout)findViewById(R.id.payment_group);
        btn_pay=(Button)findViewById(R.id.btn_pay);
        edt_amount=(EditText)findViewById(R.id.edt_amount);

        new getToken().execute();

        btn_pay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitPayment();
            }
        });
    }

    private void submitPayment() {
        DropInRequest dropInRequest=new DropInRequest()
          .clientToken(token)
                .amount(""+edt_amount.getText().toString())
                .requestThreeDSecureVerification(Settings.isThreeDSecureEnabled(this))
                .collectDeviceData(Settings.shouldCollectDeviceData(this))
                .googlePaymentRequest(getGooglePaymentRequest())
                .maskCardNumber(true)
                .maskSecurityCode(true)
                .androidPayCart(getAndroidPayCart())
                .androidPayShippingAddressRequired(Settings.isAndroidPayShippingAddressRequired(this))
                .androidPayPhoneNumberRequired(Settings.isAndroidPayPhoneNumberRequired(this))
                .androidPayAllowedCountriesForShipping(Settings.getAndroidPayAllowedCountriesForShipping(this))
                .vaultManager(true);

        if (Settings.isPayPalAddressScopeRequested(this)) {
            dropInRequest.paypalAdditionalScopes(Collections.singletonList(PayPal.SCOPE_ADDRESS));
        }
        startActivityForResult(dropInRequest.getIntent(this),REQUEST_CODE);
    }

    private GooglePaymentRequest getGooglePaymentRequest() {
        return new GooglePaymentRequest()
                .transactionInfo(TransactionInfo.newBuilder()
                        .setTotalPrice(""+edt_amount.getText().toString())
                        .setCurrencyCode("EUR")
                        .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                        .build())
                .emailRequired(true);
    }

    private Cart getAndroidPayCart() {
        return Cart.newBuilder()
                .setCurrencyCode(Settings.getAndroidPayCurrency(this))
                .setTotalPrice(""+edt_amount.getText().toString())
                .addLineItem(LineItem.newBuilder()
                        .setCurrencyCode("EUR")
                        .setDescription("Description")
                        .setQuantity("1")
                        .setUnitPrice(""+edt_amount.getText().toString())
                        .setTotalPrice(""+edt_amount.getText().toString())
                        .build())
                .build();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        Log.e("payment_happen1","result code:"+resultCode+" / request Code:"+requestCode);

        if(resultCode == RESULT_OK){
            if(requestCode == REQUEST_CODE){
                DropInResult result=data.getParcelableExtra(DropInResult.EXTRA_DROP_IN_RESULT);
                PaymentMethodNonce nonce =result.getPaymentMethodNonce();
                String strNonce =nonce.getNonce();
                if(!edt_amount.getText().toString().isEmpty()){
                    amount =edt_amount.getText().toString();
                    paramsHash=new HashMap<>();
                    paramsHash.put("amount",amount);
                    paramsHash.put("nonce",strNonce);

                    Log.e("payment_happen2","  Amount :"+amount+" / Nonce:"+strNonce);

                    sendPayments();
                }
                else{
                    Toast.makeText(this, "Please enter the valid amount..", Toast.LENGTH_SHORT).show();
                }
            }
            else if(resultCode == RESULT_CANCELED){
                Toast.makeText(this, "User Cancel..", Toast.LENGTH_SHORT).show();
            }
            else{
                Exception error=(Exception)data.getSerializableExtra(DropInActivity.EXTRA_ERROR);
                Log.e("payment_error1",error.toString());
            }

        } else if(requestCode == GOOGLE_PAYMENT_REQUEST && resultCode == RESULT_OK) {
            GooglePayment.tokenize(mBraintreeFragment, PaymentData.getFromIntent(data));
        }
    }


    private void sendPayments() {
        RequestQueue queue=Volley.newRequestQueue(MainActivity.this);
        StringRequest stringRequest=new StringRequest(Request.Method.POST, API_CHECK_OUT,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        if(response.toString().contains("Successful")){
                            Toast.makeText(MainActivity.this, "Transcation Successful..", Toast.LENGTH_SHORT).show();
                        }
                        else{
                            Toast.makeText(MainActivity.this, "Transcation Failed!!", Toast.LENGTH_SHORT).show();
                        }
                        Log.e("payment_error2",response.toString());
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("payment_error3",error.toString());
            }
        })
        {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                if(paramsHash==null){
                    return  null;
                }
                Map<String,String>params = new HashMap<>();
                for(String key:paramsHash.keySet()){
                    params.put(key,paramsHash.get(key));
                }
                return params;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String,String>params =new HashMap<>();
                params.put("Content-Type","application/x-www-form-urlencoded");
                return params;
            }
        };
        queue.add(stringRequest);
    }

    private class getToken extends AsyncTask {
        ProgressDialog mDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mDialog = new ProgressDialog(MainActivity.this,android.R.style.Theme_DeviceDefault_Dialog);
            mDialog.setCancelable(false);
            mDialog.setMessage("Please Wait..");
            mDialog.show();
        }

        @Override
        protected Object doInBackground(Object[] objects) {
            HttpClient client =new HttpClient();
            client.get(API_GET_TOKEN, new HttpResponseCallback() {
                @Override
                public void success(final String responseBody) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            waiting_group.setVisibility(View.GONE);
                            payment_group.setVisibility(View.VISIBLE);
                            /*Set Token*/
                            token=responseBody;

                            Log.e("token_frm_server",""+responseBody);

                        }
                    });
                }

                @Override
                public void failure(Exception exception) {
                    Log.e("payment_error4",exception.toString());
                }
            });
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            mDialog.dismiss();
        }
    }
}
